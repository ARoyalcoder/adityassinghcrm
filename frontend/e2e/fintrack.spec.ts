import { test, expect } from '@playwright/test'

test('capture fintrack screenshot and verify components', async ({ page }) => {
  // Set desktop viewport at 1440x900
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/fintrack')

  // Verify FinTrack branding & sidebar
  await expect(page.getByText('FinTrack')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Dashboard', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Accounts', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Transactions', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Budget', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Goals', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Investments', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Reports', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Settings', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Help & Support', exact: true })).toBeVisible()

  // Verify Header
  await expect(page.getByRole('heading', { name: /Good morning!/i })).toBeVisible()
  await expect(page.getByPlaceholder('Search...')).toBeVisible()

  // Verify Financial Summary Cards
  await expect(page.getByText('Total Balance')).toBeVisible()
  await expect(page.getByText('₹2,45,680')).toBeVisible()
  await expect(page.getByText('Total Income')).toBeVisible()
  await expect(page.getByText('₹1,20,850')).toBeVisible()
  await expect(page.getByText('Total Expenses')).toBeVisible()
  await expect(page.getByText('₹78,900').first()).toBeVisible()
  await expect(page.getByText('Savings This Month')).toBeVisible()
  await expect(page.getByText('₹41,950')).toBeVisible()

  // Verify Spending Overview
  await expect(page.getByRole('heading', { name: 'Spending Overview' })).toBeVisible()
  await expect(page.getByText('Housing')).toBeVisible()
  await expect(page.getByText('Food & Dining').first()).toBeVisible()

  // Verify Recent Transactions
  await expect(page.getByRole('heading', { name: 'Recent Transactions' })).toBeVisible()
  await expect(page.getByText('Starbucks Coffee')).toBeVisible()
  await expect(page.getByText('Uber Ride')).toBeVisible()
  await expect(page.getByText('Salary from ABC Ltd.')).toBeVisible()
  await expect(page.getByText('Amazon Shopping')).toBeVisible()

  // Verify Goals
  await expect(page.getByRole('heading', { name: 'Goals Progress' })).toBeVisible()
  await expect(page.getByText('Vacation in Bali')).toBeVisible()
  await expect(page.getByText('Buy New Laptop')).toBeVisible()

  // Verify Smart Tip
  await expect(page.getByText('Smart Tip')).toBeVisible()
  await expect(page.getByText("You're saving 15% more this month. Keep it up! 🎉")).toBeVisible()

  // Wait for images to render cleanly and take screenshot
  await page.waitForTimeout(1000)
  await page.screenshot({ path: 'public/images/fintrack/fintrack-rendered-screenshot.png', fullPage: false })
})
