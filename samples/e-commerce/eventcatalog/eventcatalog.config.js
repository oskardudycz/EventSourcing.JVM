import path from 'path';
import url from 'url';

const __dirname = path.dirname(url.fileURLToPath(import.meta.url));
const spec = (file) => path.join(__dirname, 'specs', file);
const domain = { id: 'e-commerce', name: 'E-Commerce', version: '1.0.0' };

/** @type {import('@eventcatalog/core/bin/eventcatalog.config').Config} */
export default {
  cId: '8e791336-dea4-4273-be27-0d543568d7da',
  // required by eventcatalog
  tsd: 1794833529336,
  title: 'E-Commerce',
  tagline: 'The modules of the e-commerce sample, their endpoints and their channels',
  organizationName: 'Event Sourcing on the JVM',
  homepageLink: 'https://event-driven.io/?utm_source=event_sourcing_jvm',
  repositoryUrl: 'https://github.com/oskardudycz/EventSourcing.JVM',
  trailingSlash: false,
  base: '/',
  generators: [
    [
      '@eventcatalog/generator-openapi',
      {
        services: [
          { path: spec('openapi-shopping_carts.json'), id: 'shopping-carts', name: 'Shopping carts', owners: ['shopping-carts'] },
          { path: spec('openapi-orders.json'), id: 'orders', name: 'Orders', owners: ['orders'] },
        ],
        domain,
      },
    ],
    [
      '@eventcatalog/generator-asyncapi',
      {
        services: [
          { path: spec('asyncapi-shopping_carts.json'), id: 'shopping-carts', name: 'Shopping carts', owners: ['shopping-carts'] },
          { path: spec('asyncapi-orders.json'), id: 'orders', name: 'Orders', owners: ['orders'] },
          { path: spec('asyncapi-payments.json'), id: 'payments', name: 'Payments', owners: ['payments'] },
          { path: spec('asyncapi-shipments.json'), id: 'shipments', name: 'Shipments', owners: ['shipments'] },
        ],
        domain,
      },
    ],
  ],
};
