import { test, expect } from '@playwright/test';
import path from 'path';

test('should upload an image and show nutrition facts', async ({ page }) => {
  // Mock the create job API
  await page.route('**/api/jobs', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'text/plain',
        body: 'test-job-123',
      });
    } else {
      await route.continue();
    }
  });

  // Mock the job status API - first call is processing, second is complete
  let pollCount = 0;
  await page.route('**/api/jobs/test-job-123', async (route) => {
    pollCount++;
    if (pollCount === 1) {
      await route.fulfill({
        status: 204, // Still processing
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: "completed",
          nutrition_facts_label: {
            title: "Test Milk",
            servings_per_container: "1",
            calories: "120",
            total_fat: { quantity: "5", quantity_unit: "g" },
            nutrients: [
              { name: "Calcium", quantity: "300", quantity_unit: "mg", percentage_daily_value: "20" }
            ]
          }
        }),
      });
    }
  });

  // Go to the app
  await page.goto('/');

  // Wait for the app to load (initial status text)
  const status = page.locator('h2.status-text').first();
  await expect(status).toContainText('Select image to begin');

  // Upload the file
  const filePath = path.resolve(__dirname, '../docs/examples/example1.jpeg');
  await page.setInputFiles('input[type="file"]', filePath);

  // Check for intermediate status
  await expect(status).toHaveText(/uploading|processing|Analysis complete/);

  // Wait for completed state
  await expect(status).toHaveText('Analysis complete', { timeout: 30000 });

  // Verify the nutrition card is shown
  const card = page.locator('.nutrition-card');
  await expect(card).toBeVisible();
  await expect(card).toContainText('Test Milk');
  await expect(page.locator('.nf-calories-value')).toHaveText('120');
});
