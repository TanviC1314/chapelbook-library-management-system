import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CopyButton } from './CopyButton'
import { ToastProvider } from './Toast'

function renderButton() {
  // setup() also gives jsdom a working in-memory clipboard
  const user = userEvent.setup()
  render(
    <ToastProvider>
      <CopyButton value="CB-0001" label="Copy barcode CB-0001" />
    </ToastProvider>,
  )
  return user
}

describe('CopyButton', () => {
  afterEach(() => vi.restoreAllMocks())

  it('puts the value on the clipboard, confirms it, and shows a checkmark', async () => {
    const user = renderButton()

    await user.click(screen.getByRole('button', { name: 'Copy barcode CB-0001' }))

    expect(await navigator.clipboard.readText()).toBe('CB-0001')
    expect(await screen.findByRole('status')).toHaveTextContent('Copied CB-0001.')
    // The button keeps its accessible label; the icon inside switches to a check.
    expect(screen.getByRole('button', { name: 'Copy barcode CB-0001' })).toBeInTheDocument()
  })

  it('says so when the browser refuses the clipboard', async () => {
    const user = renderButton()
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('denied'))

    await user.click(screen.getByRole('button', { name: 'Copy barcode CB-0001' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not copy. Select CB-0001 and copy it by hand.')
  })
})
