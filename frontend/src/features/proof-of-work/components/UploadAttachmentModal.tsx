import { useState } from 'react'
import { Upload, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { ATTACHMENT_TYPES } from '../api'

interface UploadAttachmentModalProps {
  isOpen: boolean
  onClose: () => void
  onUpload: (
    file: File,
    type: 'BEFORE_PHOTO' | 'DURING_PHOTO' | 'AFTER_PHOTO' | 'DOCUMENT',
    caption?: string
  ) => Promise<void>
  isUploading?: boolean
}

export function UploadAttachmentModal({
  isOpen,
  onClose,
  onUpload,
  isUploading = false,
}: UploadAttachmentModalProps) {
  const [file, setFile] = useState<File | null>(null)
  const [attachmentType, setAttachmentType] = useState<
    'BEFORE_PHOTO' | 'DURING_PHOTO' | 'AFTER_PHOTO' | 'DOCUMENT'
  >('BEFORE_PHOTO')
  const [caption, setCaption] = useState('')

  if (!isOpen) return null

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!file) return

    await onUpload(file, attachmentType, caption.trim() || undefined)
    setFile(null)
    setCaption('')
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4">
      <div className="bg-card w-full max-w-md rounded-xl border p-6 shadow-xl space-y-4">
        <div className="flex items-center justify-between pb-3 border-b">
          <h2 className="text-lg font-semibold">Upload Photo or Document</h2>
          <Button variant="ghost" size="sm" onClick={onClose} disabled={isUploading}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-medium">Attachment Type *</label>
            <Select
              value={attachmentType}
              onChange={(e) =>
                setAttachmentType(
                  e.target.value as 'BEFORE_PHOTO' | 'DURING_PHOTO' | 'AFTER_PHOTO' | 'DOCUMENT'
                )
              }
              disabled={isUploading}
            >
              {ATTACHMENT_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </Select>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">Select File *</label>
            <Input
              type="file"
              accept="image/*,application/pdf"
              required
              onChange={(e) => setFile(e.target.files?.[0] || null)}
              disabled={isUploading}
            />
            {file && (
              <p className="text-[11px] text-muted-foreground">
                Selected: {file.name} ({(file.size / 1024).toFixed(1)} KB)
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-medium">Caption / Description</label>
            <Input
              placeholder="e.g. Main breaker panel prior to disassembly"
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              disabled={isUploading}
            />
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t">
            <Button type="button" variant="outline" onClick={onClose} disabled={isUploading}>
              Cancel
            </Button>
            <Button type="submit" disabled={isUploading || !file}>
              <Upload className="h-4 w-4 mr-1.5" />
              {isUploading ? 'Uploading...' : 'Upload'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
