import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 把 MainActivity 里 syncRequest 的逻辑原样搬出来，在本机对着 dsh-sync 跑一遍。
 * 覆盖：URL 拼接、配对码头、JSON PUT/GET、二进制 PUT/GET、401、连接失败提示。
 *
 * 用法：java SyncTransportTest.java http://127.0.0.1:62999 <配对码>
 */
public class SyncTransportTest {

    private static String base = "";
    private static String token = "";

    /** 与 MainActivity.syncRequest 一致：base 去尾斜杠、去掉重复的 /dsh-sync 前缀。 */
    static String request(String method, String path, String bodyBase64, String contentType) {
        String normalized = base.replaceAll("/+$", "");
        if (normalized.endsWith("/dsh-sync")) {
            normalized = normalized.substring(0, normalized.length() - "/dsh-sync".length());
        }
        String url = normalized + "/dsh-sync/" + path;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("X-Pair-Token", token);
            if (contentType != null && !contentType.isEmpty()) {
                connection.setRequestProperty("Content-Type", contentType);
            }
            if (bodyBase64 != null && !bodyBase64.isEmpty()) {
                byte[] payload = Base64.getDecoder().decode(bodyBase64);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(payload.length);
                OutputStream out = connection.getOutputStream();
                out.write(payload);
                out.close();
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            if (input != null) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = input.read(chunk)) > 0) {
                    buffer.write(chunk, 0, read);
                }
                input.close();
            }
            return "{\"status\":" + status + ",\"body\":\""
                    + Base64.getEncoder().encodeToString(buffer.toByteArray()) + "\"}";
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return "{\"error\":\"无法连接电脑（" + url + "）：" + reason + "\"}";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static String body(String response) {
        int start = response.indexOf("\"body\":\"");
        if (start < 0) {
            return "";
        }
        int end = response.indexOf('"', start + 8);
        return new String(Base64.getDecoder().decode(response.substring(start + 8, end)), StandardCharsets.UTF_8);
    }

    static int status(String response) {
        int start = response.indexOf("\"status\":");
        if (start < 0) {
            return -1;
        }
        int end = response.indexOf(',', start);
        return Integer.parseInt(response.substring(start + 9, end));
    }

    static int pass = 0;
    static int fail = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("PASS  " + name);
        } else {
            fail++;
            System.out.println("FAIL  " + name + "  ->  " + detail);
        }
    }

    public static void main(String[] args) {
        base = args.length > 0 ? args[0] : "http://127.0.0.1:62999";
        token = args.length > 1 ? args[1] : "783968";

        // 1. hello（无需鉴权）
        String hello = request("GET", "hello", "", "");
        check("hello 200 + app 字段", status(hello) == 200 && body(hello).contains("\"app\""), hello);

        // 2. 错误配对码 -> 401
        token = "000000";
        String denied = request("GET", "index", "", "");
        check("错误配对码 401", status(denied) == 401, denied);
        token = args.length > 1 ? args[1] : "783968";

        // 3. PUT 会话
        String sessionJson = "{\"id\":\"s-java\",\"title\":\"Java 传输测试\",\"pinned\":false,"
                + "\"current_message_id\":1,\"inserted_at\":\"2026-11-19T15:00:00.000Z\","
                + "\"updated_at\":\"2026-11-19T16:00:00.000Z\",\"messages\":[]}";
        String put = request("PUT", "sessions/s-java",
                Base64.getEncoder().encodeToString(sessionJson.getBytes(StandardCharsets.UTF_8)),
                "application/json");
        check("PUT 会话 200", status(put) == 200, put);

        // 4. GET 会话内容一致
        String got = request("GET", "sessions/s-java", "", "");
        check("GET 会话内容一致", status(got) == 200 && body(got).equals(sessionJson), body(got));

        // 5. 二进制 PUT/GET（1x1 PNG）
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        String filePath = "files/chat_sessions/s-java_files/pixel.png?session=s-java";
        String putFile = request("PUT", filePath, Base64.getEncoder().encodeToString(png), "application/octet-stream");
        check("PUT 二进制 200", status(putFile) == 200, putFile);
        String gotFile = request("GET", filePath, "", "");
        // body() 按 UTF-8 解析文本，二进制要用原始 base64 段比较
        int start = gotFile.indexOf("\"body\":\"");
        int end = gotFile.indexOf('"', start + 8);
        byte[] downloaded = Base64.getDecoder().decode(gotFile.substring(start + 8, end));
        check("二进制逐字节一致", java.util.Arrays.equals(png, downloaded),
                "期望 " + png.length + " 字节，实际 " + downloaded.length + " 字节");

        // 6. DELETE 会话
        String del = request("DELETE", "sessions/s-java", "", "");
        check("DELETE 200", status(del) == 200, del);
        String gone = request("GET", "sessions/s-java", "", "");
        check("删除后 GET 404", status(gone) == 404, gone);

        // 7. 连接失败的提示
        String savedBase = base;
        base = "http://127.0.0.1:9";
        String failed = request("GET", "index", "", "");
        base = savedBase;
        check("连接失败返回友好提示", failed.contains("无法连接电脑"), failed);

        // 8. 地址里带了 /dsh-sync 前缀也能用
        base = savedBase + "/dsh-sync";
        String prefixed = request("GET", "index", "", "");
        base = savedBase;
        check("地址含 /dsh-sync 前缀", status(prefixed) == 200, prefixed);

        System.out.println();
        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
