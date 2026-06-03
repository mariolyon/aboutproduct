import { test, expect } from '@playwright/test';
import path from 'path';

test.describe('UI Snapshot Tests', () => {
  // Standardize the viewport size to ensure consistent snapshot dimensions across platforms
  test.use({ viewport: { width: 1280, height: 800 } });

  test('Initial state snapshot', async ({ page }) => {
    await page.goto('/');
    // Wait for the app to load (initial status text)
    const status = page.locator('h2.status-text').first();
    await expect(status).toContainText('Select image to begin');
    await expect(page).toHaveScreenshot('initial-state.png', { animations: 'disabled' });
  });

  test('Processing state snapshot', async ({ page }) => {
    // Mock the create job API
    await page.route('**/api/jobs', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'text/plain',
        body: 'processing-job-123',
      });
    });

    // Mock job status API to be always processing
    await page.route('**/api/jobs/processing-job-123', async (route) => {
      await route.fulfill({
        status: 204, // Still processing
      });
    });

    await page.goto('/');
    const status = page.locator('h2.status-text').first();
    await expect(status).toContainText('Select image to begin');

    const filePath = path.resolve(__dirname, '../docs/examples/example1.jpg');
    await page.setInputFiles('input[type="file"]', filePath);

    // Wait for uploading/processing state
    await expect(status).toHaveText(/uploading|processing/);

    await expect(page).toHaveScreenshot('processing-state.png', { animations: 'disabled' });
  });

  test('Completed state, JSON modal, and Title editing snapshots', async ({ page }) => {
    // Mock create job API
    await page.route('**/api/jobs', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'text/plain',
        body: 'completed-job-123',
      });
    });

    // Mock completed job status API
    await page.route('**/api/jobs/completed-job-123', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: "completed",
          nutrition_facts_label: {
            title: "Premium Whole Milk",
            servings_per_container: "8",
            calories: "150",
            total_fat: { quantity: "8", quantity_unit: "g" },
            nutrients: [
              { name: "Calcium", quantity: "300", quantity_unit: "mg", percentage_daily_value: "20" },
              { name: "Iron", quantity: "0", quantity_unit: "mg", percentage_daily_value: "0" }
            ],
            ingredients: ["Milk", "Vitamin D3"]
          }
        }),
      });
    });

    await page.goto('/');
    const status = page.locator('h2.status-text').first();
    const filePath = path.resolve(__dirname, '../docs/examples/example1.jpg');
    await page.setInputFiles('input[type="file"]', filePath);

    // Wait for completed state
    await expect(status).toHaveText('Analysis complete', { timeout: 15000 });
    const card = page.locator('.nutrition-card');
    await expect(card).toBeVisible();

    // 1. Single product view snapshot
    await expect(page).toHaveScreenshot('single-product-completed.png', { animations: 'disabled' });

    // 2. Open JSON Modal snapshot
    await page.click('button:has-text("View JSON")');
    await expect(page.locator('h3:has-text("Nutrition Facts JSON")')).toBeVisible();
    await expect(page).toHaveScreenshot('json-modal-open.png', { animations: 'disabled' });

    // Close modal
    await page.click('button:has-text("Close")');
    await expect(page.locator('h3:has-text("Nutrition Facts JSON")')).not.toBeVisible();

    // 3. Title editing snapshot
    await page.locator('.nutrition-card h2', { hasText: 'Premium Whole Milk' }).click();
    await expect(page.locator('input[type="text"]')).toBeVisible();
    await expect(page).toHaveScreenshot('title-editing-mode.png', { animations: 'disabled' });
  });

  test('Comparison and History Drawer snapshots', async ({ page }) => {
    // Mock the create job API
    let jobCount = 0;
    await page.route('**/api/jobs', async (route) => {
      jobCount++;
      await route.fulfill({
        status: 200,
        contentType: 'text/plain',
        body: `compare-job-${jobCount}`,
      });
    });

    // Mock job status API for product A
    await page.route('**/api/jobs/compare-job-1', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: "completed",
          nutrition_facts_label: {
            title: "Product Creamy A",
            calories: "100",
            calories_per_100: "200",
            total_fat: { quantity: "1", quantity_unit: "g", quantity_per_100: "2" },
            ingredients: ["Ingredients A"]
          }
        }),
      });
    });

    // Mock job status API for product B
    await page.route('**/api/jobs/compare-job-2', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: "completed",
          nutrition_facts_label: {
            title: "Product Milky B",
            calories: "200",
            calories_per_100: "400",
            total_fat: { quantity: "5", quantity_unit: "g", quantity_per_100: "10" },
            ingredients: ["Ingredients B"]
          }
        }),
      });
    });

    await page.goto('/');

    const filePath1 = path.resolve(__dirname, '../docs/examples/example1.jpg');
    const filePath2 = path.resolve(__dirname, '../docs/examples/example2.jpg');

    // Upload first image
    await page.setInputFiles('input[type="file"]', filePath1);
    const status = page.locator('h2.status-text').first();
    await expect(status).toHaveText('Analysis complete', { timeout: 15000 });
    await expect(page.locator('.nutrition-card')).toContainText('Product Creamy A');

    // Upload second image
    await page.setInputFiles('input[type="file"]', filePath2);
    await expect(status).toHaveText('Analysis complete', { timeout: 15000 });
    await expect(page.locator('.nutrition-card')).toContainText('Product Milky B');

    // Open History Drawer
    await page.click('button:has-text("History")');
    const historyItemA = page.locator('.flex.justify-between.items-center.p-3', { hasText: 'Product Creamy A' });
    const historyItemB = page.locator('.flex.justify-between.items-center.p-3', { hasText: 'Product Milky B' });
    await expect(historyItemA).toBeVisible({ timeout: 15000 });
    await expect(historyItemB).toBeVisible({ timeout: 15000 });

    // Take history drawer snapshot with timestamps masked to avoid flakiness from dynamic dates/times
    await expect(page).toHaveScreenshot('history-drawer-open.png', {
      animations: 'disabled',
      mask: [page.locator('.flex.justify-between.items-center.p-3 span.text-xs')]
    });

    // Compare products
    const currentTitle = await page.locator('.nutrition-card h2').first().textContent();
    const otherProduct = currentTitle?.includes('Product Creamy A') ? 'Product Milky B' : 'Product Creamy A';
    const otherItem = otherProduct === 'Product Creamy A' ? historyItemA : historyItemB;
    await otherItem.locator('button:has-text("Compare")').click();

    // Verify comparison view is visible
    await expect(page.locator('h2:has-text("Product Comparison")')).toBeVisible();

    // Take comparison snapshot
    await expect(page).toHaveScreenshot('comparison-view.png', { animations: 'disabled' });
  });
});
