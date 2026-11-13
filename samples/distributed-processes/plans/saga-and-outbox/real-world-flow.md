# Real-world order flow: research notes

Researched on 2026-11-13. Facts only, with sources. The last section compares them with the sample's code, using the sample's own names.

## 1. Stock

- A stock hold with a timer exists only before the order is placed, while the client is in the cart or checkout.
  - commercetools `ReserveOnCart`: "Reservations remain active until the Cart is ordered or the reservation expires." The documented example is 30 minutes. ([Reserve stock on Cart](https://docs.commercetools.com/tutorials/reserve-stock-on-cart))
  - Salesforce B2C Commerce: if `CreateOrder` is not called for a basket with reserved items, "reservations for this basket will automatically expire after 10 minutes". ([ReserveInventoryForOrder](https://documentation.b2c.commercecloud.salesforce.com/DOC1/topic/com.demandware.dochelp/DWAPI/pipeletapi/html/api/pipelet.ReserveInventoryForOrder.html))
- After the order is placed, stock is committed to it, with no timer, until it ships or the order is cancelled.
  - Shopify: "Committed inventory refers to the number of units that are part of a placed order but aren't yet fulfilled." Placing an order moves units from available to committed. Fulfilling the order removes them from committed. ([Inventory states](https://help.shopify.com/en/manual/products/inventory/managing-inventory-quantities/inventory-states))
  - Magento MSI: a reservation is created when the order is placed. The shipment adds a compensating reservation and deducts the stock. A cancellation adds a compensating reservation that gives the stock back. No source mentions an expiry. ([Order status and reservations](https://experienceleague.adobe.com/en/docs/commerce-admin/inventory/basics/order-status), [Inventory and shipment](https://docs.magento.com/user-guide/v2.3/catalog/inventory-orders-shipment.html))
  - commercetools `ReserveOnOrder`: the stock is checked and deducted when the order is created. ([Inventory overview](https://docs.commercetools.com/api/inventory-overview))
- In the warehouse, allocation "is the process of reserving specific stock for an order. It happens when you release an order to the warehouse." It tells the warehouse what to pick. ([ShipHawk WMS: pick](https://shiphawk.com/wms-docs/pick.html))

## 2. Fulfilment in the warehouse

- Steps: allocation, pick, pack (the parcel label is printed here), ship confirmation, and handover to the carrier, who collects the parcels. ([Infios: pick pack ship](https://www.infios.com/en/knowledge-center/glossary/wms-pick-pack-ship), [HotWax: order fulfillment](https://docs.hotwax.co/documents/learn-hotwax-oms/business-processes/order-fulfillment))
- When a separate fulfilment service (a warehouse run by another company) ships the order, the shop sends it a fulfilment request, and the service answers it. Shopify's request statuses ([FulfillmentOrderRequestStatus](https://shopify.dev/docs/api/admin-graphql/latest/enums/fulfillmentorderrequeststatus)):
  - `SUBMITTED`: the shop requested fulfilment.
  - `ACCEPTED` or `REJECTED`: the service accepted or rejected the request.
  - `CANCELLATION_REQUESTED`: the shop asked to cancel an accepted request.
  - `CANCELLATION_ACCEPTED` or `CANCELLATION_REJECTED`: the service accepted or rejected the cancellation.
  - `CLOSED`: "The fulfillment service closed the fulfillment order without completing it."
- What happens to the order in each case ([Shopify: fulfilment by an app](https://help.shopify.com/en/manual/fulfillment/fulfilling-orders/app-fulfillment)):
  - Request sent: the order is "In progress", and the shop waits for the answer.
  - Request rejected: "the order status returns to Unfulfilled".
  - Cancelled before acceptance: the shop cancels directly, and the order goes back to Unfulfilled.
  - After acceptance, the shop can only ask for a cancellation. If the service accepts, the order goes back to Unfulfilled. If it rejects, "the fulfillment service continues the fulfillment process".

## 3. Shipping label and carrier

- Creating a label is one call to a carrier API, made directly or through a service such as ShipStation or EasyPost. The answer contains the label and the tracking number.
- A failed label call is fixed and retried:
  - An invalid address is a data problem. It is fixed first ("Try using our address validation API"). ([ShipStation error codes](https://docs.shipstation.com/apis/shipengine/docs/reference/errors-codes))
  - A timeout or a carrier outage: "Wait 5-10 minutes and retry label creation." ShipStation has "Retry All Failures". ([ShipStation: batch errors](https://help.shipstation.com/hc/en-us/articles/360026138131))
- An unused label can be refunded only if the carrier never scanned it: USPS within 30 days, UPS and FedEx within 90 days. ([EasyPost: refunds](https://docs.easypost.com/docs/shipping-refund), [EasyPost: refunding labels](https://support.easypost.com/hc/en-us/articles/360042414392-Refunding-Labels))
- Tracking statuses (EasyPost): `unknown`, `pre_transit` (label created, not scanned yet), `in_transit`, `out_for_delivery`, `delivered`, `available_for_pickup`, `return_to_sender`, `failure`, `cancelled` and `error`. A failed delivery attempt that will be retried stays `in_transit`. Without a retry, it becomes `return_to_sender`, `available_for_pickup` or `failure`, depending on the carrier. ([EasyPost trackers](https://docs.easypost.com/docs/trackers), [Understanding trackers](https://support.easypost.com/hc/en-us/articles/360044353091-Understanding-Trackers))

## 4. Payment

- The card is authorised at checkout and captured later (Stripe `capture_method=manual`). ([Stripe: place a hold](https://docs.stripe.com/payments/place-a-hold-on-a-payment-method))
- How long an authorisation lasts:
  - Online card payments started by the client: 7 days on all major networks.
  - Online Visa payments started by the merchant: 5 days, since 14 April 2024.
  - Stripe says to read the real deadline from `capture_before`.
  
  ([Stripe: place a hold](https://docs.stripe.com/payments/place-a-hold-on-a-payment-method))
- Shopify: the standard period is 7 days, and up to 30 days on Shopify Plus. Shopify can email a warning 1 day before the authorisation expires and shows "Expiring" 2 days before. After that, the payment status is "Expired": the payment was not captured in time. ([Shopify: payment authorization](https://help.shopify.com/en/manual/payments/payment-authorization), [Shopify: order statuses](https://help.shopify.com/en/manual/orders/order-status))
- When the authorisation expires, the funds are released and the Stripe PaymentIntent becomes `canceled`. ([Stripe: place a hold](https://docs.stripe.com/payments/place-a-hold-on-a-payment-method))
- I found no documented way in Shopify to authorise the same order again after it expires.
- Capture comes after shipping. Processor terms: "Don't submit a Transaction for processing until after the goods have been shipped". ([Fiserv: your payment acceptance](https://merchants.fiserv.com/content/dam/firstdata/sg/en/documents/your_payment_acceptance.pdf))
- When an uncaptured order is cancelled, the authorisation is voided. Shopify's `orderCancel`: authorised payments "will be voided regardless of the refund setting". ([orderCancel](https://shopify.dev/docs/api/admin-graphql/latest/mutations/orderCancel))

## 5. Order cancellation

- Shopify allows cancelling only before fulfilment starts. Restock is on by default, and an uncaptured payment is voided. After fulfilment, the way back is a refund or a return. ([Shopify: canceling orders](https://help.shopify.com/en/manual/orders/cancel-delete-order))
- With a fulfilment service, a cancellation after the service accepted the request is only a request, and the service may reject it (section 2).

## 6. Completion

- Shopify has the setting "After an order has been fulfilled and paid, or when all items have been refunded: Automatically archive the order". ([Shopify: order processing](https://help.shopify.com/en/manual/checkout-settings/order-processing))
- commercetools `OrderState` has the values `Open`, `Confirmed`, `Complete` and `Cancelled`. `Complete` means the order is fulfilled. ([commercetools Orders](https://docs.commercetools.com/api/projects/orders))
- In both, completion means fulfilled and paid. Neither waits for delivery.
- A fulfilment service first accepts the request, then creates the fulfilment with the tracking number when the parcel is packed and ready to ship. This is from the Shopify community forum, a secondhand source. ([Shopify community](https://community.shopify.com/t/tracking-numbers-fulfillmentorders-replacement-for-fulfillments-update-tracking-json/189031))

## 7. Compared with the sample's code

As of 2026-11-13, after step 5.

| Sample code | Real-world counterpart | Difference |
|---|---|---|
| `ReserveStock` → `StockReserved` after `OrderInitialized` | Stock committed to a placed order | Matches: no timer. |
| `RequestFulfillment` → `FulfillmentRequested` | Fulfilment request to a warehouse service | Matches. |
| `ConfirmPackageSent(carrier, trackingNumber)` → `PackageWasSent` | The service creates the fulfilment with tracking | Matches. There is no separate "accepted" step. |
| `RejectFulfillment` → `FulfillmentRejected` | Request rejected, order back to Unfulfilled | The sample cancels the order, because it has no merchant step. |
| `DeliverPackage` → `PackageWasDelivered` | Tracking status `delivered` | Only the shipment records it. |
| `OrderCompleted` on sent and captured | Shopify auto-archive and commercetools `Complete` on fulfilled and paid | Matches. |
| `AuthorizePayment`, capture after `PackageWasSent`, `VoidPayment` on cancel | Same | Matches. |
| `AuthorizationExpiryWorker` before shipping → order cancelled | Authorisation window, "Expired" | Matches the deadline. A real shop may ask for payment again first. |
| `RetryOrderPayment` after shipping, `CloseUnpaidOrder` after the retry period | Amazon asks the customer to revise the payment method | The retry period is a setting; the research found no standard length. |
| `CancelOrder` only before `OrderConfirmed` | Cancel only before fulfilment starts | Matches. No cancellation request to the warehouse. |
| (none) | Label call failures, failed delivery, `return_to_sender`, returns | Not modelled. |
