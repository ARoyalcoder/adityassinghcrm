import { useState } from 'react'
import {
  Camera,
  CheckCircle2,
  Clock,
  FileCheck,
  FileText,
  Image as ImageIcon,
  Plus,
  ShieldCheck,
  Trash2,
  UserCheck,
  Upload,
  AlertCircle,
  CheckSquare,
  Square,
  ExternalLink,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { useHasPermission } from '@/features/auth/auth-context'
import {
  type ProofItemDetail,
  type TimelineView,
  type CreateProofItemInput,
  type CustomerConfirmationInput,
} from '../api'
import { CreateProofModal } from './CreateProofModal'
import { UploadAttachmentModal } from './UploadAttachmentModal'
import { CustomerSignatureModal } from './CustomerSignatureModal'

interface ProofTimelineProps {
  timeline: TimelineView
  entityType: 'PROJECT' | 'JOB'
  entityId: string
  onCreateItem: (input: CreateProofItemInput) => Promise<void>
  onUploadAttachment: (
    itemId: string,
    file: File,
    type: 'BEFORE_PHOTO' | 'DURING_PHOTO' | 'AFTER_PHOTO' | 'DOCUMENT',
    caption?: string
  ) => Promise<void>
  onToggleChecklist: (itemId: string, checklistId: string, completed: boolean) => Promise<void>
  onCustomerConfirmation: (itemId: string, input: CustomerConfirmationInput) => Promise<void>
  onVerifyItem: (itemId: string, remarks?: string) => Promise<void>
  onDeleteItem: (itemId: string, reason: string) => Promise<void>
  isLoading?: boolean
}

export function ProofTimeline({
  timeline,
  entityType,
  entityId,
  onCreateItem,
  onUploadAttachment,
  onToggleChecklist,
  onCustomerConfirmation,
  onVerifyItem,
  onDeleteItem,
}: ProofTimelineProps) {
  const canCreate = useHasPermission('PROOF_OF_WORK_CREATE')
  const canVerify = useHasPermission('PROOF_OF_WORK_VERIFY')
  const canDelete = useHasPermission('PROOF_OF_WORK_DELETE')

  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [activeUploadItemId, setActiveUploadItemId] = useState<string | null>(null)
  const [activeSignItemId, setActiveSignItemId] = useState<string | null>(null)
  const [deleteConfirmItemId, setDeleteConfirmItemId] = useState<string | null>(null)
  const [deleteReason, setDeleteReason] = useState('')
  const [verifyRemarksItemId, setVerifyRemarksItemId] = useState<string | null>(null)
  const [verifyRemarks, setVerifyRemarks] = useState('')
  const [filterCategory, setFilterCategory] = useState<string>('ALL')

  const items = timeline.items.filter((item) => {
    if (filterCategory === 'ALL') return true
    return item.category === filterCategory
  })

  const handleDelete = async (itemId: string) => {
    if (!deleteReason.trim()) return
    await onDeleteItem(itemId, deleteReason.trim())
    setDeleteConfirmItemId(null)
    setDeleteReason('')
  }

  const handleVerify = async (itemId: string) => {
    await onVerifyItem(itemId, verifyRemarks.trim() || undefined)
    setVerifyRemarksItemId(null)
    setVerifyRemarks('')
  }

  return (
    <div className="space-y-6">
      {/* Metrics Row */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1">
            <span className="text-xs text-muted-foreground font-medium">Total Proof Records</span>
            <p className="text-xl font-bold">{timeline.totalItems}</p>
          </CardContent>
        </Card>
        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1">
            <span className="text-xs text-muted-foreground font-medium flex items-center gap-1 text-emerald-600">
              <ShieldCheck className="h-3.5 w-3.5" />
              Verified by PM
            </span>
            <p className="text-xl font-bold text-emerald-600">{timeline.verifiedItems}</p>
          </CardContent>
        </Card>
        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1">
            <span className="text-xs text-muted-foreground font-medium flex items-center gap-1 text-indigo-600">
              <UserCheck className="h-3.5 w-3.5" />
              Customer Signed
            </span>
            <p className="text-xl font-bold text-indigo-600">{timeline.customerConfirmedItems}</p>
          </CardContent>
        </Card>
        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1">
            <span className="text-xs text-muted-foreground font-medium flex items-center gap-1 text-sky-600">
              <Camera className="h-3.5 w-3.5" />
              Photos Uploaded
            </span>
            <p className="text-xl font-bold text-sky-600">{timeline.photoCount}</p>
          </CardContent>
        </Card>
        <Card className="shadow-sm">
          <CardContent className="p-3.5 space-y-1">
            <span className="text-xs text-muted-foreground font-medium flex items-center gap-1 text-amber-600">
              <FileCheck className="h-3.5 w-3.5" />
              Technical Docs
            </span>
            <p className="text-xl font-bold text-amber-600">{timeline.documentCount}</p>
          </CardContent>
        </Card>
      </div>

      {/* Action Bar & Filter */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-2 overflow-x-auto text-xs">
          <Button
            variant={filterCategory === 'ALL' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setFilterCategory('ALL')}
          >
            All Items ({timeline.items.length})
          </Button>
          <Button
            variant={filterCategory === 'BEFORE_PHOTO' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setFilterCategory('BEFORE_PHOTO')}
          >
            Before Photos
          </Button>
          <Button
            variant={filterCategory === 'DURING_PHOTO' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setFilterCategory('DURING_PHOTO')}
          >
            During Photos
          </Button>
          <Button
            variant={filterCategory === 'AFTER_PHOTO' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setFilterCategory('AFTER_PHOTO')}
          >
            After Photos
          </Button>
          <Button
            variant={filterCategory === 'CHECKLIST' ? 'secondary' : 'ghost'}
            size="sm"
            onClick={() => setFilterCategory('CHECKLIST')}
          >
            Checklists
          </Button>
        </div>

        {canCreate && (
          <Button onClick={() => setCreateModalOpen(true)} size="sm" className="gap-1.5 shrink-0">
            <Plus className="h-4 w-4" />
            Record Proof of Work
          </Button>
        )}
      </div>

      {/* Timeline Stream */}
      {items.length === 0 ? (
        <Card className="border-dashed">
          <CardContent className="p-10 text-center space-y-3">
            <Camera className="h-10 w-10 text-muted-foreground mx-auto" />
            <div className="space-y-1">
              <p className="font-medium text-sm">No Proof of Work records yet</p>
              <p className="text-xs text-muted-foreground">
                Document before/after site photos, technical tests, checklists and client sign-offs.
              </p>
            </div>
            {canCreate && (
              <Button size="sm" onClick={() => setCreateModalOpen(true)}>
                Record First Proof
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-3 before:bottom-3 before:w-0.5 before:bg-border">
          {items.map((item) => (
            <ProofItemCard
              key={item.id}
              item={item}
              canVerify={canVerify}
              canDelete={canDelete}
              canCreate={canCreate}
              onOpenUpload={() => setActiveUploadItemId(item.id)}
              onOpenSign={() => setActiveSignItemId(item.id)}
              onOpenVerify={() => setVerifyRemarksItemId(item.id)}
              onOpenDelete={() => setDeleteConfirmItemId(item.id)}
              onToggleChecklist={(checklistId, completed) =>
                onToggleChecklist(item.id, checklistId, completed)
              }
            />
          ))}
        </div>
      )}

      {/* Modals */}
      <CreateProofModal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        onSubmit={onCreateItem}
        entityType={entityType}
        entityId={entityId}
      />

      <UploadAttachmentModal
        isOpen={!!activeUploadItemId}
        onClose={() => setActiveUploadItemId(null)}
        onUpload={(file, type, caption) =>
          onUploadAttachment(activeUploadItemId!, file, type, caption)
        }
      />

      <CustomerSignatureModal
        isOpen={!!activeSignItemId}
        onClose={() => setActiveSignItemId(null)}
        onSubmit={(input) => onCustomerConfirmation(activeSignItemId!, input)}
      />

      {/* Delete Confirmation Modal */}
      {deleteConfirmItemId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4">
          <div className="bg-card w-full max-w-md rounded-xl border p-6 shadow-xl space-y-4">
            <div className="flex items-center gap-2 text-destructive font-semibold">
              <AlertCircle className="h-5 w-5" />
              Confirm Safe Deletion
            </div>
            <p className="text-xs text-muted-foreground">
              Unauthorized deletion is strictly prevented. Non-verified proof items may be archived
              only with an explicit audit justification recorded in the system audit logs.
            </p>
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Audit Reason *</label>
              <textarea
                className="w-full rounded-md border p-2 text-xs bg-background"
                rows={3}
                placeholder="Specify reason for deletion/archival (e.g. Duplicate entry recorded by mistake)..."
                value={deleteReason}
                onChange={(e) => setDeleteReason(e.target.value)}
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setDeleteConfirmItemId(null)
                  setDeleteReason('')
                }}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                size="sm"
                disabled={!deleteReason.trim()}
                onClick={() => handleDelete(deleteConfirmItemId)}
              >
                Archive with Audit Log
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Verification Remarks Modal */}
      {verifyRemarksItemId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4">
          <div className="bg-card w-full max-w-md rounded-xl border p-6 shadow-xl space-y-4">
            <div className="flex items-center gap-2 text-emerald-600 font-semibold">
              <ShieldCheck className="h-5 w-5" />
              Project Manager Verification
            </div>
            <p className="text-xs text-muted-foreground">
              Verifying this proof certifies quality inspection. Once verified, this proof item
              becomes immutable and cannot be deleted.
            </p>
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Verification Remarks (Optional)</label>
              <textarea
                className="w-full rounded-md border p-2 text-xs bg-background"
                rows={2}
                placeholder="Inspected wiring gauge and inverter earthing; approved."
                value={verifyRemarks}
                onChange={(e) => setVerifyRemarks(e.target.value)}
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setVerifyRemarksItemId(null)
                  setVerifyRemarks('')
                }}
              >
                Cancel
              </Button>
              <Button
                className="bg-emerald-600 hover:bg-emerald-700 text-white"
                size="sm"
                onClick={() => handleVerify(verifyRemarksItemId)}
              >
                Sign & Verify
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function ProofItemCard({
  item,
  canVerify,
  canDelete,
  canCreate,
  onOpenUpload,
  onOpenSign,
  onOpenVerify,
  onOpenDelete,
  onToggleChecklist,
}: {
  item: ProofItemDetail
  canVerify: boolean
  canDelete: boolean
  canCreate: boolean
  onOpenUpload: () => void
  onOpenSign: () => void
  onOpenVerify: () => void
  onOpenDelete: () => void
  onToggleChecklist: (checklistId: string, completed: boolean) => void
}) {
  const isPhoto =
    item.category === 'BEFORE_PHOTO' ||
    item.category === 'DURING_PHOTO' ||
    item.category === 'AFTER_PHOTO'

  return (
    <div className="relative group">
      {/* Timeline Node Icon */}
      <div className="absolute -left-6 top-1.5 flex h-6 w-6 -translate-x-1/2 items-center justify-center rounded-full bg-background border-2 border-primary ring-4 ring-background">
        {item.verified ? (
          <ShieldCheck className="h-3 w-3 text-emerald-600" />
        ) : item.customerConfirmed ? (
          <UserCheck className="h-3 w-3 text-indigo-600" />
        ) : isPhoto ? (
          <Camera className="h-3 w-3 text-sky-600" />
        ) : (
          <Clock className="h-3 w-3 text-muted-foreground" />
        )}
      </div>

      <Card className="shadow-sm">
        <CardHeader className="p-4 pb-2">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className="text-[11px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-muted text-muted-foreground">
                  {item.category.replace('_', ' ')}
                </span>
                <span className="text-xs text-muted-foreground">
                  {new Date(item.recordedAt).toLocaleString()}
                </span>
              </div>
              <h3 className="font-semibold text-base">{item.title}</h3>
            </div>

            <div className="flex items-center gap-1.5">
              {item.verified ? (
                <span className="inline-flex items-center gap-1 text-[11px] font-medium px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
                  <ShieldCheck className="h-3.5 w-3.5" />
                  Verified by {item.verifiedByName}
                </span>
              ) : canVerify ? (
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 text-xs gap-1 border-emerald-300 hover:bg-emerald-50 text-emerald-700"
                  onClick={onOpenVerify}
                >
                  <ShieldCheck className="h-3.5 w-3.5" />
                  Verify
                </Button>
              ) : null}

              {canDelete && !item.verified && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-7 w-7 p-0 text-muted-foreground hover:text-destructive"
                  title="Archive proof record"
                  onClick={onOpenDelete}
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              )}
            </div>
          </div>
        </CardHeader>

        <CardContent className="p-4 pt-2 space-y-4">
          {/* Technician Notes */}
          {item.technicianNotes && (
            <div className="text-xs bg-muted/40 p-3 rounded-lg space-y-1">
              <div className="flex items-center justify-between text-muted-foreground font-medium">
                <span>Technician Observations</span>
                <span>{item.technicianName ?? 'Technician'}</span>
              </div>
              <p className="whitespace-pre-wrap">{item.technicianNotes}</p>
            </div>
          )}

          {/* Checklist Section */}
          {item.checklists && item.checklists.length > 0 && (
            <div className="space-y-2 border rounded-lg p-3">
              <div className="flex items-center justify-between text-xs font-medium">
                <span className="flex items-center gap-1.5">
                  <CheckSquare className="h-4 w-4 text-primary" />
                  Checklist Completion
                </span>
                <span className="text-muted-foreground">
                  {item.checklists.filter((c) => c.completed).length}/{item.checklists.length} Done
                </span>
              </div>
              <ul className="space-y-1.5">
                {item.checklists.map((check) => (
                  <li
                    key={check.id}
                    onClick={() => {
                      if (!item.verified && canCreate) {
                        onToggleChecklist(check.id, !check.completed)
                      }
                    }}
                    className={`flex items-center justify-between text-xs p-2 rounded transition-colors ${
                      !item.verified && canCreate ? 'cursor-pointer hover:bg-muted/60' : ''
                    } ${check.completed ? 'bg-muted/30 text-muted-foreground line-through' : ''}`}
                  >
                    <div className="flex items-center gap-2">
                      {check.completed ? (
                        <CheckSquare className="h-4 w-4 text-emerald-600 shrink-0" />
                      ) : (
                        <Square className="h-4 w-4 text-muted-foreground shrink-0" />
                      )}
                      <span>{check.itemTitle}</span>
                    </div>
                    {check.completed && (
                      <span className="text-[10px] text-muted-foreground">
                        {check.completedByName} ·{' '}
                        {check.completedAt ? new Date(check.completedAt).toLocaleTimeString() : ''}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Attachment Gallery (Photos & Documents) */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground flex items-center gap-1">
                <ImageIcon className="h-3.5 w-3.5" />
                Attachments ({item.attachments?.length ?? 0})
              </span>
              {!item.verified && canCreate && (
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 text-xs gap-1"
                  onClick={onOpenUpload}
                >
                  <Upload className="h-3 w-3" />
                  Add Photo / Doc
                </Button>
              )}
            </div>

            {item.attachments && item.attachments.length > 0 ? (
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                {item.attachments.map((att) => {
                  const isImg = att.contentType.startsWith('image/')
                  return (
                    <div
                      key={att.id}
                      className="group/att border rounded-lg overflow-hidden bg-muted/20 flex flex-col justify-between"
                    >
                      {isImg ? (
                        <a
                          href={att.downloadUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="aspect-video bg-muted relative overflow-hidden block"
                        >
                          <img
                            src={att.downloadUrl}
                            alt={att.caption ?? att.fileName}
                            className="w-full h-full object-cover transition-transform group-hover/att:scale-105"
                          />
                          <div className="absolute top-1 left-1">
                            <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-black/70 text-white uppercase">
                              {att.attachmentType.replace('_PHOTO', '')}
                            </span>
                          </div>
                        </a>
                      ) : (
                        <div className="aspect-video bg-muted/50 flex flex-col items-center justify-center p-3 text-center">
                          <FileText className="h-7 w-7 text-muted-foreground mb-1" />
                          <span className="text-[11px] font-medium line-clamp-1">{att.fileName}</span>
                        </div>
                      )}

                      <div className="p-2 text-[11px] space-y-1">
                        {att.caption && (
                          <p className="font-medium line-clamp-1 text-xs">{att.caption}</p>
                        )}
                        <div className="flex items-center justify-between text-muted-foreground text-[10px]">
                          <span>{(att.sizeBytes / 1024).toFixed(0)} KB</span>
                          <a
                            href={att.downloadUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="hover:text-primary flex items-center gap-0.5"
                          >
                            View
                            <ExternalLink className="h-2.5 w-2.5" />
                          </a>
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground italic">No photos or documents uploaded yet.</p>
            )}
          </div>

          {/* Customer Confirmation / Sign-off */}
          <div className="border-t pt-3">
            {item.customerConfirmed ? (
              <div className="bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-900 rounded-lg p-3 space-y-2">
                <div className="flex items-center justify-between text-xs text-emerald-800 dark:text-emerald-300 font-semibold">
                  <span className="flex items-center gap-1.5">
                    <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                    Customer Sign-off Recorded
                  </span>
                  <span>{item.confirmedAt ? new Date(item.confirmedAt).toLocaleDateString() : ''}</span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <div>
                    <span className="text-muted-foreground">Signer: </span>
                    <span className="font-medium">{item.customerName}</span>
                  </div>
                  {item.customerRemarks && (
                    <p className="italic text-muted-foreground">"{item.customerRemarks}"</p>
                  )}
                </div>
                {item.customerSignature && (
                  <div className="pt-1">
                    <span className="text-[10px] text-muted-foreground block mb-1">
                      Customer Signature
                    </span>
                    <img
                      src={item.customerSignature}
                      alt="Customer Signature"
                      className="h-12 border bg-white rounded p-1 object-contain"
                    />
                  </div>
                )}
              </div>
            ) : !item.verified && canCreate ? (
              <div className="flex items-center justify-between">
                <span className="text-xs text-muted-foreground">Pending customer confirmation</span>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 text-xs gap-1"
                  onClick={onOpenSign}
                >
                  <UserCheck className="h-3.5 w-3.5" />
                  Record Customer Sign-off
                </Button>
              </div>
            ) : (
              <span className="text-xs text-muted-foreground">No customer confirmation recorded.</span>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
