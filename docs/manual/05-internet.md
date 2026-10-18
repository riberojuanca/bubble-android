# 5. Internet access

## The bubble's firewall

Inside the bubble **no app has internet unless you allow it**. Bubble does it with a small VPN that runs inside the bubble: apps you did not allow are sent into a tunnel where their traffic is dropped; apps you allowed are left out of the VPN and use the network normally. Nothing is inspected or forwarded, and none of your traffic leaves the phone through Bubble.

This VPN belongs only to the bubble: your normal phone and its apps are not affected, and you can still use your own VPN outside.

## Wi-Fi and mobile data, per app

In **Bubble → Apps**, each app has two icons:

| Icon | Lilac | Grey, crossed out |
|---|---|---|
| **Wi-Fi** | The app has internet when the phone is on Wi-Fi | No internet on Wi-Fi |
| **Mobile data** | The app has internet when the phone is on mobile data | No internet on mobile data |

Tap an icon to change it. The change is saved at once and applied in the background; you can keep tapping other apps.

Examples:

- **NewPipe or Spotify on Wi-Fi only**, so they do not use your data plan.
- **Signal on both**, so messages arrive on the street.
- **A banking app on mobile data only**, never on public Wi-Fi.

The firewall follows the network the phone is using: when you move from Wi-Fi to mobile data, it switches to the mobile data list within a moment. Ethernet counts as Wi-Fi.

## Things to know

- **Changing permissions works without Wi-Fi.** Bubble talks to its copy inside the bubble directly.
- **If the bubble is locked** when you change an icon, the choice is saved and applied when you unlock it.
- **When the bubble opens**, its firewall starts by blocking everything and then asks Bubble for your list. If it cannot get the list, all apps of the bubble stay without internet and Bubble shows a notification: *Apps in this space have no internet*. Open Bubble to send it again.
- **Right after unlocking** there is an instant, before the firewall starts, in which an app could reach the network. Keyboards and most apps do not run in that instant.
- **The firewall chooses per network, not per packet**: it applies the list of the network the phone is using. An app allowed only on Wi-Fi does not get mobile data, but if the phone had both networks at once and the app chose the other one by itself, the firewall would not stop it. In normal use the phone uses one network at a time.

## The Meta space

The Meta space has its own firewall with the same two icons per app, in **Bubble → Meta space**. Apps you add there have internet on both networks from the start; its keyboard has none.

## Your normal phone

To cut the internet of apps on your normal phone, use the [phone's firewall](09-phone-firewall.md).
