import { test, expect } from '@playwright/test';
import path from 'path';

test('should compare two products and allow title editing', async ({ page }) => {
  // Mock the create job API
  let jobCount = 0;
  await page.route('**/api/jobs', async (route) => {
    jobCount++;
    await route.fulfill({
      status: 200,
      contentType: 'text/plain',
      body: `job-${jobCount}`,
    });
  });

  // Mock the job status API
  await page.route('**/api/jobs/job-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: "completed",
        nutrition_facts_label: {
          title: "Product A",
          calories: "100",
          calories_per_100: "200",
          total_fat: { quantity: "1", quantity_unit: "g", quantity_per_100: "2" }
        }
      }),
    });
  });

  await page.route('**/api/jobs/job-2', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: "completed",
        nutrition_facts_label: {
          title: "Product B",
          calories: "200",
          calories_per_100: "400",
          total_fat: { quantity: "5", quantity_unit: "g", quantity_per_100: "10" }
        }
      }),
    });
  });

  // Go to the app
  await page.goto('/');

  // Upload both images at once
  const filePath1 = path.resolve(__dirname, '../docs/examples/example1.jpg');
  const filePath2 = path.resolve(__dirname, '../docs/examples/example2.jpg');
  await page.setInputFiles('input[type="file"]', [filePath1, filePath2]);

  // Wait for a product in main view (either A or B depending on race)
  await expect(page.locator('.nutrition-card')).toBeVisible();

  // Open history
  await page.click('button:has-text("History")');

  // Wait for scan history items specifically
  const historyItemA = page.locator('.flex.justify-between.items-center.p-3', { hasText: 'Product A' });
  const historyItemB = page.locator('.flex.justify-between.items-center.p-3', { hasText: 'Product B' });

  await expect(historyItemA).toBeVisible({ timeout: 15000 });
  await expect(historyItemB).toBeVisible({ timeout: 15000 });

  // Click "Compare" on the product that is NOT currently in the main view
  const currentTitle = await page.locator('.nutrition-card h2').first().textContent();
  const otherProduct = currentTitle?.includes('Product A') ? 'Product B' : 'Product A';
  const otherItem = otherProduct === 'Product A' ? historyItemA : historyItemB;

  await otherItem.locator('button:has-text("Compare")').click();

  // Verify comparison view
  await expect(page.locator('h2:has-text("Product Comparison")')).toBeVisible();
  const comparisonCard = page.locator('.nutrition-card');
  await expect(comparisonCard).toContainText('Product A');
  await expect(comparisonCard).toContainText('Product B');

  // Verify comparison columns (actual vs /100g)
  await expect(comparisonCard).toContainText('Actual');
  await expect(comparisonCard).toContainText('/100g');

  // Verify calorie values (serving and per 100g)
  await expect(comparisonCard).toContainText('100');
  await expect(comparisonCard).toContainText('200');
  await expect(comparisonCard).toContainText('400');

  // Verify total fat serving and per 100g values
  await expect(comparisonCard).toContainText('1 g');
  await expect(comparisonCard).toContainText('2 g');
  await expect(comparisonCard).toContainText('5 g');
  await expect(comparisonCard).toContainText('10 g');

  // Exit comparison
  await page.click('button:has-text("Exit Comparison")');

  // Edit title of whichever product is now visible
  const visibleTitle = await page.locator('.nutrition-card h2').first().textContent();
  const cleanTitle = visibleTitle?.trim() || "";
  const newTitle = `Improved ${cleanTitle}`;

  // Click the title to edit - be specific to the nutrition card h2
  await page.locator('.nutrition-card h2', { hasText: cleanTitle }).click();
  await page.fill('input[type="text"]', newTitle);
  await page.click('button:has-text("Save")');

  // Verify updated title in card
  await expect(page.locator('.nutrition-card h2')).toContainText(newTitle);

  // Verify updated title in history
  await page.click('button:has-text("History")');
  await expect(page.locator('.flex.justify-between.items-center.p-3', { hasText: newTitle })).toBeVisible();
});
