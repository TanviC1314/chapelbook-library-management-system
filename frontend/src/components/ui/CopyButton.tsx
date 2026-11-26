import { Copy } from 'lucide-react'
import { useToast } from './Toast'

/** Copies a short value, such as a barcode, to the clipboard and says whether it worked. */
export function CopyButton({ value, label }: { value: string; label: string }) {
  const toast = useToast()

  async function copy() {
    try {
      // Browsers only offer the clipboard on HTTPS or localhost, so this can fail and must say so.
      await navigator.clipboard.writeText(value)
      toast.success(`Copied ${value}.`)
    } catch {
      toast.error(`Could not copy. Select ${value} and copy it by hand.`)
    }
  }

  return (
    <button
      type="button"
      onClick={() => void copy()}
      aria-label={label}
      title={label}
      className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-2 focus-visible:outline-brand-600"
    >
      <Copy className="h-4 w-4" aria-hidden="true" />
    </button>
  )
}
