import path from 'path';
import url from 'url';

const __dirname = path.dirname(url.fileURLToPath(import.meta.url));
const spec = (file) => path.join(__dirname, 'specs', file);
const modules = [
  { id: 'shopping-carts', name: 'Shopping carts', spec: 'shopping_carts', web: true, domain: { id: 'shopping-carts', name: 'Shopping carts', version: '1.0.0' } },
  { id: 'orders', name: 'Orders', spec: 'orders', web: true, domain: { id: 'orders', name: 'Orders', version: '1.0.0' } },
  { id: 'payments', name: 'Payments', spec: 'payments', web: false, domain: { id: 'payments', name: 'Payments', version: '1.0.0' } },
  { id: 'shipments', name: 'Shipments', spec: 'shipments', web: false, domain: { id: 'shipments', name: 'Shipments', version: '1.0.0' } },
];
const service = (module, kind) => ({
  path: spec(`${kind}-${module.spec}.json`),
  id: `${module.id}-service`,
  name: module.name,
  owners: [module.id],
});

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
  generators: modules.flatMap((module) => [
    ...(module.web
      ? [['@eventcatalog/generator-openapi', { services: [service(module, 'openapi')], domain: module.domain }]]
      : []),
    ['@eventcatalog/generator-asyncapi', { services: [service(module, 'asyncapi')], domain: module.domain, parseChannels: true }],
  ]),
};
