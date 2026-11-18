import clsx from 'clsx'
import { useEffect, useState } from 'react'
import styles from './ChatInput.module.css'

type RecentPhoto = { id: string; name: string }

const bridge = () =>
  (window as unknown as { DSNative?: { recentPhotos?: () => string; pickPhoto?: (id: string) => void } })
    .DSNative

/**
 * 官方风格的「+」面板：内联在输入框下方，不遮罩页面。
 * 有相册权限时补一条最近照片，点选后由原生把图片回传给草稿。
 */
export const AttachPanel = ({
  selectedPhotos,
  onRemove,
  onCamera,
  onGallery,
  onFile,
}: {
  selectedPhotos: Map<string, string>
  onRemove: (attachmentId: string) => void
  onCamera: () => void
  onGallery: () => void
  onFile: () => void
}) => {
  const [photos, setPhotos] = useState<RecentPhoto[]>([])

  useEffect(() => {
    const load = () => {
      try {
        const raw = bridge()?.recentPhotos?.()
        if (typeof raw === 'string' && raw.startsWith('[')) {
          setPhotos(JSON.parse(raw) as RecentPhoto[])
        }
      } catch {
        // 取不到照片就只显示三个入口
      }
    }
    load()
    const scope = window as unknown as { __dsPhotosReady?: () => void }
    scope.__dsPhotosReady = load
    return () => {
      delete scope.__dsPhotosReady
    }
  }, [])

  return (
    <div className={styles.attachPanel}>
      {photos.length > 0 ? (
        <div className={styles.photoStrip}>
          {photos.map((photo) => {
            const attachmentId = selectedPhotos.get(photo.id)
            const selected = attachmentId !== undefined
            return (
              <button
                key={photo.id}
                type="button"
                className={clsx(styles.photoItem, selected && styles.photoSelected)}
                onClick={() => {
                  if (selected) {
                    onRemove(attachmentId)
                  } else {
                    bridge()?.pickPhoto?.(photo.id)
                  }
                }}
                aria-label={photo.name}
                aria-pressed={selected}
              >
                <img src={`/__thumb?id=${encodeURIComponent(photo.id)}`} alt={photo.name} />
                {selected ? (
                  <span className={styles.photoCheck}>
                    <CheckIcon />
                  </span>
                ) : null}
              </button>
            )
          })}
        </div>
      ) : null}
      <div className={styles.attachTiles}>
        <button type="button" className={styles.attachTile} onClick={onCamera}>
          <CameraIcon />
          <span className={styles.attachLabel}>拍照</span>
        </button>
        <button type="button" className={styles.attachTile} onClick={onGallery}>
          <PhotoIcon />
          <span className={styles.attachLabel}>相册</span>
        </button>
        <button type="button" className={styles.attachTile} onClick={onFile}>
          <FileIcon />
          <span className={styles.attachLabel}>文件</span>
        </button>
      </div>
    </div>
  )
}

const CheckIcon = () => (
  <svg viewBox="0 0 16 16" fill="none" aria-hidden>
    <path
      d="M3.6 8.4l2.7 2.7 6-6.2"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
)

const CameraIcon = () => (
  <svg className={styles.attachIcon} viewBox="0 0 24 24" fill="none" aria-hidden>
    <path
      d="M9.2 4.5 8 6.6H5.2A1.7 1.7 0 0 0 3.5 8.3v10a1.7 1.7 0 0 0 1.7 1.7h13.6a1.7 1.7 0 0 0 1.7-1.7v-10a1.7 1.7 0 0 0-1.7-1.7H16l-1.2-2.1z"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinejoin="round"
    />
    <circle cx="12" cy="13" r="3.6" stroke="currentColor" strokeWidth="1.6" />
  </svg>
)

const PhotoIcon = () => (
  <svg className={styles.attachIcon} viewBox="0 0 24 24" fill="none" aria-hidden>
    <rect
      x="3.5"
      y="4.5"
      width="17"
      height="15"
      rx="2.4"
      stroke="currentColor"
      strokeWidth="1.6"
    />
    <circle cx="8.6" cy="9.4" r="1.6" fill="currentColor" />
    <path
      d="m4.2 17.2 4.6-4.6 3.4 3.4 2.5-2.5 5.1 5.1"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinejoin="round"
    />
  </svg>
)

const FileIcon = () => (
  <svg className={styles.attachIcon} viewBox="0 0 24 24" fill="none" aria-hidden>
    <path
      d="M14 3.8H7.4A1.7 1.7 0 0 0 5.7 5.5v13a1.7 1.7 0 0 0 1.7 1.7h9a1.7 1.7 0 0 0 1.7-1.7V8z"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinejoin="round"
    />
    <path d="M14 3.8V8h4.1" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
  </svg>
)
