// 用 acorn 按指定 ECMAScript 版本解析构建产物，确认没有超出目标版本的语法。
// 用法：node syntax-check.js <dist 目录> [ecmaVersion=2018]
const fs = require('fs')
const path = require('path')
const acorn = require(process.env.ACORN_PATH || 'acorn')

const root = process.argv[2] || 'dist'
const ecmaVersion = Number(process.argv[3] || 2018)

const walk = (dir, out = []) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, out)
    else if (entry.name.endsWith('.js')) out.push(full)
  }
  return out
}

const files = walk(root)
let failures = 0
for (const file of files) {
  const source = fs.readFileSync(file, 'utf8')
  let ok = false
  let message = ''
  for (const sourceType of ['script', 'module']) {
    try {
      acorn.parse(source, { ecmaVersion, sourceType })
      ok = true
      break
    } catch (error) {
      message = error.message
    }
  }
  if (!ok) {
    failures += 1
    console.log('FAIL', path.relative(root, file), '-', message)
  }
}

console.log(`ecmaVersion ${ecmaVersion}: checked ${files.length} files, failures ${failures}`)
process.exit(failures === 0 ? 0 : 1)
