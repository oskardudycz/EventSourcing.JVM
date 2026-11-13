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

## 7. Cart

Researched in Shopify, WooCommerce, Amazon and Allegro. These shops document little of their internals, so several facts come from help pages and community forums. Each item says which.

- Starting a cart: the client does not open a cart. The action is "Add to cart", and the first product starts the cart. Amazon's help on placing an order starts with adding items to the cart. ([Amazon: how to place an order](https://digprjsurvey.amazon.com/csad/help/node/TM0z2tvxdI4nu36ypt))
- When the client is known:
  - Allegro: a client can fill a cart without logging in. At login, the items move "z koszyków anonimowych … do koszyków użytkowników" (from anonymous carts to the user's cart). The cart belongs to the account, not to the device. Buying requires an account. ([Allegro forum: anonymous carts](https://spolecznosc.allegro.pl/t5/dyskusje-kupuj%C4%85cych/przedmioty-same-pojawiaj%C4%85-si%C4%99-w-koszyku/m-p/351423), [android.com.pl: purchases only for registered users](https://android.com.pl/news/400605-od-dzis-zakupy-na-allegro-tylko-dla-zarejestrowanych-uzytkownikow/))
  - WooCommerce: a guest's cart is kept in the session. At login, it is merged with the cart saved for the account. ([Business Bloomer: cart merge](https://www.businessbloomer.com/woocommerce-cart-merge-sessions-changes/?mtke=488))
  - Shopify: guest checkout is allowed when the merchant makes customer accounts optional. `cartBuyerIdentityUpdate` can "associate a logged-in customer" with an existing cart. ([cartBuyerIdentityUpdate](https://shopify.dev/docs/api/storefront/latest/mutations/cartBuyerIdentityUpdate))
  - The sample follows Allegro: the client is logged in from the first product.
- Price: the cart does not keep the price from when the item was added.
  - Amazon's cart page: "The Cart is a temporary place to store a list of your items and reflects each item's most recent price." ([Amazon cart](https://us.amazon.com/gp/cart/view.html?ref_=nav_cart))
  - Amazon tells the client when a price changed: "Please note that the price of ___ has increased from $9.75 to $11.20 since you placed it in your Shopping Cart." This is quoted from users' screenshots. ([AnandTech forum](https://forums.anandtech.com/threads/amazon-com-slowly-increasing-prices.1587029/post-15555653))
  - Shopify checkout can show "Prices for these items have changed and are updated in your cart." ([Shopify community](https://community.shopify.com/t/prices-for-these-items-have-changed-and-are-updated-in-your-cart/284837))
  - Allegro: adding to the cart does not reserve the product or guarantee its price, and the cart shows no notification when a price changes. ([Allegro forum: price change after adding to cart](https://spolecznosc.allegro.pl/t5/dyskusje-kupuj%C4%85cych/zmiana-cen-po-dodaniu-do-koszyka/td-p/657648))
- Stock: the cart holds no stock (Allegro, above).
  - WooCommerce creates the order with "Pending payment" when the client clicks "Place order", before the payment, and empties the cart. ([WordPress.org support](https://wordpress.org/support/?p=10149006), [remarpro mirror of WordPress.org support](https://remarpro.com/support/topic/woocommerc-order-created-even-when-the-payment-is-cancelled))
  - WooCommerce "Hold stock (minutes)" holds the stock for an unpaid order. When the time ends, the order is cancelled and the stock goes back. ([Business Bloomer: cancel unpaid orders](https://www.businessbloomer.com/woocommerce-automatically-cancel-orders/?mtke=144), [wc_cancel_unpaid_orders](https://wp-kama.com/function/wc_cancel_unpaid_orders))
  - Stock is checked at checkout, and the client who finishes first gets the product. Shopify shows "Some items are no longer available"; WooCommerce shows "not enough in stock". ([Shopify community](https://community.shopify.com/t/inventory-issues-some-items-are-no-longer-available-your-cart-has-been-updated/210416), [WordPress.org support](https://wordpress.org/support/topic/customers-get-error-message-when-they-try-to-buy-all-stock/))
- Leaving the cart for the order:

  | Shop | From the cart | Last action |
  |---|---|---|
  | Amazon | "Proceed to checkout" | "Place your order" |
  | Shopify | "Check out" | "Pay now"; the order is created after the payment is authorised |
  | WooCommerce | "Proceed to checkout" | "Place order"; the order is created before the payment |
  | Allegro | "Do kasy" | "Kupuję i płacę" |

  ([Amazon: how to place an order](https://digprjsurvey.amazon.com/csad/help/node/TM0z2tvxdI4nu36ypt), [Shopify: order processing](https://help.shopify.com/en/manual/checkout-settings/order-processing), [PayTabs: Shopify order workflow](https://support.paytabs.com/en/support/solutions/articles/60000908136-3-1-shopify-plugin-order-workflow), [Allegro forum: finishing a purchase](https://spolecznosc.allegro.pl/t5/dyskusje-kupuj%C4%85cych/jak-zako%C5%84czy%C4%87-procedur%C4%99-zakupu/td-p/1051415))
- No cancellation: none of the four shops has a "cancel cart" action. The client leaves the cart.
  - Shopify: "A cart expires within 30 days of its last update", and "Shopify automatically deletes the cart when the customer completes their checkout." ([Shopify: cart](https://shopify.dev/docs/storefronts/headless/building-with-the-storefront-api/cart))
  - Shopify sends a recovery email only for an abandoned checkout, not for a cart with no checkout. ([Shopify: abandoned checkouts](https://help.shopify.com/en/manual/orders/abandoned-checkouts))
- A price change during checkout is an accepted race. No shop locks the price list while the client checks out. The price at the click is the price of the order, and a wrong price is handled after the order, by the terms of sale.
  - Allegro: after "Kup teraz", "cena powinna pozostać taka sama, jak była w momencie, gdy kliknąłeś" (the price should stay as it was when you clicked). This is a forum answer. Allegro also limits how much a seller can raise the price of an active offer; above the limit, the seller must end the offer first. ([Allegro forum](https://spolecznosc.allegro.pl/t5/dyskusje-kupuj%C4%85cych/pytanie/m-p/705830), [Allegro developer news](https://developer.allegro.pl/news/wprowadzamy-ograniczenia-dla-zmiany-ceny-w-aktywnej-ofercie-q018my0v6Hr))
  - Amazon "only accepts your offer, and concludes the contract of sale … when it dispatches the product(s) to you and sends an e-mail confirming the dispatch". If the correct price is higher than the stated price, Amazon may contact the buyer or cancel. In 2003, Amazon UK cancelled orders for iPaqs priced at £7.32 this way. ([Amazon Haul terms](https://digprjsurvey.amazon.co.uk/csad/help/node/T1WlPQCOAi1eBINGPs), [Pinsent Masons](https://www.pinsentmasons.com/out-law/guides/amazons-pricing-error), [The Register](https://www.theregister.com/2003/03/19/no_legal_recourse_for_buyers/))
  - Shopify: I found no official source.

## 8. Compared with the sample's code

As of 2026-11-13, after step 6.

| Sample code | Real-world counterpart | Difference |
|---|---|---|
| The first `ProductItemAddedToShoppingCart` starts the cart | The first "Add to cart" starts the cart | Matches. |
| The client is logged in from the first product | Allegro; Shopify and WooCommerce also allow a guest cart | Guest carts are not modelled. |
| A line keeps the first price of its product; `CheckOutShoppingCart` checks the current prices and records `ProductItemPriceChanged` | The cart shows the current price, and the client sees a change before the order | Matches. A price change between the check and the append is accepted, as in the shops. |
| `RemoveProductItemFromShoppingCart` with a product and a quantity | The client chooses a product and a quantity | Matches. |
| `CheckOutShoppingCart` → `ShoppingCartCheckedOut` | "Proceed to checkout", "Check out", "Do kasy" | Matches the name. In the sample, the checkout places the order at once; the shops ask for delivery and payment first. |
| No cancellation | The client leaves the cart | Matches. Cart expiry is not modelled. |
| No stock held by the cart | Same | Matches. |
| `ReserveStock` → `StockReserved` after `OrderPlaced` | Stock committed to a placed order | Matches: no timer. |
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
