import { test, expect, type APIRequestContext } from "@playwright/test";

// SCRUM-4 AC1: a customer rates a delivered order end-to-end and still sees it after refreshing.
const API = process.env.VITE_API_BASE_URL || "http://localhost:8081";
const AUTH_STORAGE_KEY = "foodme.customer.auth";

async function registerCustomer(request: APIRequestContext) {
  const res = await request.post(`${API}/api/auth/register`, {
    data: {
      fullName: "Rating Customer",
      email: `rating-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`,
      phoneNumber: "+37490000000",
      password: "secret123",
    },
  });
  expect(res.ok()).toBeTruthy();
  return res.json();
}

async function placeDeliveredOrder(request: APIRequestContext, customerToken: string) {
  const chefs = await (await request.get(`${API}/api/chef/active?page=0&size=12`)).json();
  const chef = chefs.exploreChefResponseDtoList[0];
  const chefDetail = await (await request.get(`${API}/api/chef/${chef.id}`)).json();
  const dish = chefDetail.dishes[0];

  const created = await request.post(`${API}/api/order`, {
    headers: { Authorization: `Bearer ${customerToken}` },
    data: {
      chefId: chef.id,
      receiverName: "Rating Customer",
      receiverPhoneNumber: "+37490000000",
      receiverEmail: "rating@example.com",
      paymentType: "CASH",
      deliveryMethod: "TAKEAWAY",
      createOrderDishes: [{ dishId: dish.id, quantity: 1 }],
    },
  });
  expect(created.ok()).toBeTruthy();
  const { number } = await created.json();

  const login = await request.post(`${API}/admin/auth/login`, {
    data: { username: "admin", password: "admin123" },
  });
  const { token: adminToken } = await login.json();
  const order = await (await request.get(`${API}/api/order/number/${number}`)).json();
  for (const status of ["ACCEPTED", "DELIVERED"]) {
    const res = await request.patch(`${API}/admin/order/${order.id}/status`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { status },
    });
    expect(res.ok()).toBeTruthy();
  }
  return number as string;
}

test("customer rates a delivered order from My Orders and the rating persists", async ({ page, request }) => {
  const auth = await registerCustomer(request);
  const number = await placeDeliveredOrder(request, auth.token);

  // Sign in by seeding the same localStorage entry the app writes after login.
  await page.addInitScript(
    ([key, value]) => window.localStorage.setItem(key, value),
    [AUTH_STORAGE_KEY, JSON.stringify(auth)] as const,
  );
  await page.goto("/orders");

  const orderCard = page.getByRole("listitem").filter({ hasText: number });
  await orderCard.getByRole("button", { name: "Rate order" }).click();

  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  const comment = dialog.getByPlaceholder("Tell us about your order");
  await expect(comment).toBeVisible();

  // Submitting without stars is caught before any request is sent.
  await dialog.getByRole("button", { name: "Submit rating" }).click();
  await expect(dialog.getByRole("alert")).toHaveText("Choose from 1 to 5 stars.");

  // Click the visible star (the radio itself is visually hidden), then confirm the radio took it.
  await dialog.locator("label", { hasText: "4 stars" }).click();
  await expect(dialog.getByRole("radio", { name: "4 stars" })).toBeChecked();
  await comment.fill("Arrived hot, great portion.");
  await dialog.getByRole("button", { name: "Submit rating" }).click();

  await expect(dialog).toBeHidden();
  await expect(orderCard.getByRole("img", { name: "Rated 4 out of 5" })).toBeVisible();
  await expect(orderCard.getByText("Arrived hot, great portion.")).toBeVisible();
  await expect(orderCard.getByRole("button", { name: "Rate order" })).toHaveCount(0);

  // R19: still there after a refresh, and on the tracking page.
  await page.reload();
  await expect(orderCard.getByRole("img", { name: "Rated 4 out of 5" })).toBeVisible();
  await page.goto(`/tracking/${number}`);
  await expect(page.getByRole("img", { name: "Rated 4 out of 5" })).toBeVisible();
  await expect(page.getByText("Arrived hot, great portion.")).toBeVisible();

  // R9: a second attempt is refused.
  const again = await request.post(`${API}/api/customer/orders/${number}/rating`, {
    headers: { Authorization: `Bearer ${auth.token}` },
    data: { stars: 1 },
  });
  expect(again.status()).toBe(409);
  expect((await again.json()).message).toBe("Order already reviewed.");
});
