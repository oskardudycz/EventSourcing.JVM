import { test as base, expect } from '@playwright/test';

const test = base.extend({
  page: async ({ page }, use) => {
    const errors = [];
    page.on('console', (message) => {
      if (message.type() === 'error') errors.push(message.text());
    });
    page.on('pageerror', (error) => errors.push(error.message));

    await use(page);

    expect(errors, 'browser errors').toEqual([]);
  },
});

const services = {
  'shopping-carts-service': {
    name: 'Shopping carts',
    owner: { team: 'Shopping carts', member: 'Lorraine Baines' },
    specifications: ['openapi-shopping_carts', 'asyncapi-shopping_carts'],
    channels: ['shopping_carts-external'],
    outbound: ['ShoppingCartCheckedOut'],
    inbound: ['AddProductItemToShoppingCart', 'RemoveProductItemFromShoppingCart', 'CheckOutShoppingCart', 'Cart'],
  },
  'orders-service': {
    name: 'Orders',
    owner: { team: 'Orders', member: 'Marty McFly' },
    specifications: ['openapi-orders', 'asyncapi-orders'],
    channels: [
      'orders-internal',
      'orders-shopping_carts-external',
      'orders-payments-responses',
      'orders-shipments-responses',
      'payments-requests',
      'shipments-requests',
    ],
    outbound: [
      'PlaceOrder',
      'RecordOrderStockReservation',
      'RecordOrderPaymentAuthorization',
      'RecordOrderPaymentCapture',
      'RecordOrderPaymentFailure',
      'RecordOrderPackageSent',
      'RecordOrderShipmentFailure',
      'AuthorizePayment',
      'CapturePayment',
      'CancelPayment',
      'ReserveStock',
      'ReleaseStock',
      'RequestFulfillment',
    ],
    inbound: [
      'CustomerOrders',
      'OrderStatus',
      'CancelOrder',
      'RetryOrderPayment',
      'CreateCheckoutSession',
      'PlaceOrder',
      'CloseUnpaidOrder',
      'RecordOrderStockReservation',
      'RecordOrderPaymentAuthorization',
      'RecordOrderPaymentCapture',
      'RecordOrderPaymentFailure',
      'RecordOrderPackageSent',
      'RecordOrderShipmentFailure',
      'ShoppingCartCheckedOut',
      'PaymentAuthorized',
      'PaymentCaptured',
      'PaymentFailed',
      'StockReserved',
      'PackageWasSent',
      'ShipmentFailed',
    ],
  },
  'payments-service': {
    name: 'Payments',
    owner: { team: 'Payments', member: 'George McFly' },
    specifications: ['asyncapi-payments'],
    channels: ['payments-internal', 'payments-requests', 'payments-responses'],
    outbound: ['PaymentAuthorized', 'PaymentCaptured', 'PaymentFailed'],
    inbound: [
      'AuthorizePayment',
      'ConfirmPaymentAuthorization',
      'ExpirePaymentAuthorization',
      'TimeOutPayment',
      'CapturePayment',
      'ConfirmPaymentCapture',
      'FailPaymentCapture',
      'CancelPayment',
    ],
  },
  'shipments-service': {
    name: 'Shipments',
    owner: { team: 'Shipments', member: 'Emmett Brown' },
    specifications: ['asyncapi-shipments'],
    channels: ['shipments-internal', 'shipments-requests', 'shipments-responses'],
    outbound: ['StockReserved', 'PackageWasSent', 'ShipmentFailed'],
    inbound: ['ReserveStock', 'ReleaseStock', 'RequestFulfillment', 'RejectFulfillment', 'ConfirmPackageSent', 'DeliverPackage'],
  },
};

const events = [
  { name: 'ShoppingCartCheckedOut', producer: 'Shopping carts', consumer: 'Orders' },
  { name: 'PaymentAuthorized', producer: 'Payments', consumer: 'Orders' },
  { name: 'PaymentCaptured', producer: 'Payments', consumer: 'Orders' },
  { name: 'PaymentFailed', producer: 'Payments', consumer: 'Orders' },
  { name: 'StockReserved', producer: 'Shipments', consumer: 'Orders' },
  { name: 'PackageWasSent', producer: 'Shipments', consumer: 'Orders' },
  { name: 'ShipmentFailed', producer: 'Shipments', consumer: 'Orders' },
];

const sorted = (names) => [...names].sort();

const screenshots = process.env.SCREENSHOTS_DIR ?? 'test-results/screenshots';
const screenshot = (page, name) => page.screenshot({ path: `${screenshots}/${name}.png`, fullPage: true });

async function sidebarSection(page, title) {
  const header = page.getByRole('button', { name: title, exact: true });
  const section = header.locator('xpath=..');
  const items = section.locator(':scope > div > button[title]');

  await expect(header).toBeVisible();

  if ((await section.locator(':scope > div').count()) === 0) await header.click();

  await expect(items.first()).toBeVisible();
  return items.evaluateAll((buttons) => buttons.map((button) => button.title));
}

test('home page shows the e-commerce domain with its services and messages', async ({ page }) => {
  await page.goto('/');

  const counts = page.locator('#content');
  await expect(counts).toContainText('E-Commerce');
  await expect(counts).toContainText(/Domains\s*5/);
  await expect(counts).toContainText(/Flows\s*1/);
  await expect(counts).toContainText(/Services\s*4/);
  await expect(counts).toContainText(/Messages\s*38/);
  await screenshot(page, '10-eventcatalog-home');
});

test('header links to the repository', async ({ page }) => {
  await page.goto('/');

  await expect(page.locator('a[href="https://github.com/oskardudycz/EventSourcing.JVM"]').first()).toBeVisible();
});

const contexts = ['shopping-carts', 'orders', 'payments', 'shipments'];
const moduleOf = (serviceId) => serviceId.replace(/-service$/, '');

test('system context map draws the bounded contexts, the customer and the outside systems with their patterns', async ({ page }) => {
  await page.goto('/visualiser/system-context-map');

  const map = page.locator('.react-flow');
  for (const node of ['Customer', 'Shopping carts', 'Orders', 'Payments', 'Shipments', 'Payment provider', 'Fulfillment service']) {
    await expect(map.locator('.react-flow__node').filter({ hasText: node }).first()).toBeAttached();
  }
  for (const pattern of ['upstream/downstream', 'supplier → customer', 'OHS + PL', 'ACL (OrderSaga)', 'ACL (PaymentGateway)', 'ACL (FulfillmentService)']) {
    await expect(map).toContainText(pattern);
  }
  await page.setViewportSize({ width: 1800, height: 900 });
  await page.getByRole('button', { name: /fit view/i }).first().click().catch(() => {});
  await screenshot(page, '16-eventcatalog-context-map');
});

for (const service of contexts) {
  test(`${service} subdomain holds its module's service`, async ({ page }) => {
    await page.goto(`/docs/domains/${service}/1.0.0`);

    expect(await sidebarSection(page, 'Services')).toEqual([services[`${service}-service`].name]);
  });
}

test('purchase flow draws the process across the contexts', async ({ page }) => {
  await page.goto('/visualiser/flows/purchase/1.0.0');

  const diagram = page.locator('.react-flow');
  for (const step of ['Customer', 'ShoppingCartCheckedOut', 'Payment provider', 'Fulfillment service', 'CapturePayment', 'ReleaseStock', 'Order completed']) {
    await expect(diagram.locator('.react-flow__node').filter({ hasText: step }).first()).toBeAttached();
  }
});

for (const [id, service] of Object.entries(services)) {
  test(`${service.name} page shows what the module sends and receives`, async ({ page }) => {
    await page.goto(`/docs/services/${id}/1.0.0`);

    await expect(page.getByRole('heading', { name: service.name, exact: true })).toBeVisible();
    expect(sorted(await sidebarSection(page, 'Outbound Messages'))).toEqual(sorted(service.outbound));
    expect(sorted(await sidebarSection(page, 'Inbound Messages'))).toEqual(sorted(service.inbound));
  });

  test(`${service.name} page shows the team that owns it`, async ({ page }) => {
    await page.goto(`/docs/services/${id}/1.0.0`);

    const owners = page.getByRole('button', { name: 'Owners', exact: true }).locator('xpath=..').getByRole('link');
    await expect(owners).toHaveText([service.owner.team]);

    await owners.click();
    await expect(page).toHaveURL(`/docs/teams/${moduleOf(id)}`);
    await expect(page.getByText(service.owner.member).first()).toBeVisible();
  });

  test(`${service.name} architecture diagram draws the service`, async ({ page }) => {
    await page.goto(`/docs/services/${id}/1.0.0`);

    const diagram = page.locator('.react-flow');
    await expect(diagram).toBeVisible();
    await expect(diagram.locator('.react-flow__node').filter({ hasText: service.name }).first()).toBeVisible();
    if (id === 'payments-service') await screenshot(page, '11-eventcatalog-service');
  });

  for (const specification of service.specifications) {
    test(`${service.name} ${specification} specification renders`, async ({ page }) => {
      const kind = specification.startsWith('openapi') ? 'spec' : 'asyncapi';

      await page.goto(`/docs/services/${id}/1.0.0/${kind}/${specification}`);

      if (kind === 'spec') {
        await expect(page.getByText(`/customers/{customerId}/${moduleOf(id)}`).first()).toBeVisible();
      } else {
        for (const channel of service.channels) {
          await expect(page.getByText(channel, { exact: true }).filter({ visible: true }).first()).toBeVisible();
        }
      }
    });
  }
}

test('teams directory lists every team', async ({ page }) => {
  await page.goto('/directory/teams');

  for (const { owner } of Object.values(services)) {
    const row = page.getByRole('row').filter({ hasText: owner.team });
    await expect(row.first()).toBeVisible();
  }
});

test('commands listing shows every command', async ({ page }) => {
  await page.goto('/discover/commands');

  await expect(page.getByText('28 results', { exact: true })).toBeVisible();
});

test('queries listing shows every query', async ({ page }) => {
  await page.goto('/discover/queries');

  await expect(page.getByText('3 results', { exact: true })).toBeVisible();
  for (const query of ['Cart', 'CustomerOrders', 'OrderStatus']) {
    await expect(page.getByRole('row').filter({ hasText: query }).first()).toBeVisible();
  }
});

test('events listing shows who produces and who consumes each event', async ({ page }) => {
  await page.goto('/discover/events');

  await expect(page.getByText('7 results', { exact: true })).toBeVisible();
  for (const event of events) {
    const row = page.getByRole('row').filter({ hasText: event.name });
    await expect(row).toContainText(event.producer);
    await expect(row).toContainText(event.consumer);
  }
  await screenshot(page, '12-eventcatalog-events');
});

test('domain diagram draws the bounded contexts and the messages between them', async ({ page }) => {
  await page.goto('/visualiser/domains/e-commerce/1.0.0');

  const diagram = page.locator('.react-flow');
  for (const context of ['Shopping carts', 'Orders', 'Payments', 'Shipments']) {
    await expect(diagram.locator('.react-flow__node').filter({ hasText: context }).first()).toBeVisible();
  }
  await expect(diagram).toContainText('ShoppingCartCheckedOut');
  await expect(diagram).toContainText('invokes');
  await screenshot(page, '17-eventcatalog-domain-diagram');
});

const aggregates = {
  'shopping-cart': { name: 'ShoppingCart', properties: ['productItems', 'status'] },
  order: { name: 'Order', properties: ['paymentId', 'shipmentId', 'dueAt'] },
  payment: { name: 'Payment', properties: ['referenceId', 'captureBefore'] },
  shipment: { name: 'Shipment', properties: ['productItems', 'status'] },
};

for (const [id, aggregate] of Object.entries(aggregates)) {
  test(`${aggregate.name} entity page lists the aggregate's state`, async ({ page }) => {
    await page.goto(`/docs/entities/${id}/1.0.0`);

    const content = page.locator('main').first();
    await expect(content).toContainText(aggregate.name);
    for (const property of aggregate.properties) await expect(content).toContainText(property);
  });
}

test('entity map draws the aggregates and how the order refers to the others', async ({ page }) => {
  await page.goto('/visualiser/entity-map');

  const map = page.locator('.react-flow');
  for (const aggregate of Object.values(aggregates)) {
    await expect(map.locator('.react-flow__node').filter({ hasText: aggregate.name }).first()).toBeAttached();
  }
  await expect(map).toContainText('paidBy');
  await expect(map).toContainText('shippedBy');
});

test('each bounded context has its ubiquitous language', async ({ page }) => {
  const terms = { 'shopping-carts': 'Checkout', orders: 'Compensation', payments: 'Capture', shipments: 'Reservation' };
  for (const [domain, term] of Object.entries(terms)) {
    await page.goto(`/docs/domains/${domain}/language`);
    await expect(page.locator('main').first()).toContainText(term);
  }
});

test('the event store page shows every module that writes to it', async ({ page }) => {
  await page.goto('/docs/containers/event-store/1.0.0');

  const content = page.locator('main').first();
  for (const service of Object.values(services)) await expect(content).toContainText(service.name);
});

test('a channel page shows its address and the protocols that carry it', async ({ page }) => {
  await page.goto('/docs/channels/payments-requests/1.0.0');

  const content = page.locator('main').first();
  await expect(content).toContainText('Address: payments-requests');
  await expect(content).toContainText('kafka');
  await expect(content).toContainText('amqp');
});

for (const [id, state] of Object.entries({ order: 'ClosedUnpaid', payment: 'CaptureRequested', shipment: 'FulfillmentRequested' })) {
  test(`${id} entity page draws the aggregate's states`, async ({ page }) => {
    await page.goto(`/docs/entities/${id}/1.0.0`);

    const diagram = page.locator('svg[id^="mermaid"], .mermaid svg').first();
    await expect(diagram).toBeVisible();
    await expect(diagram).toContainText(state);
  });
}

test('purchase flow page draws the messages in order and what happens when a step fails', async ({ page }) => {
  await page.goto('/docs/flows/purchase/1.0.0');

  const diagrams = page.locator('svg[aria-roledescription="sequence"]');
  await expect(diagrams).toHaveCount(2);
  await expect(diagrams.first()).toContainText('OrderPaymentDue');
  await expect(diagrams.last()).toContainText('ReleaseStock');
});
