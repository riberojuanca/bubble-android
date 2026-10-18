# 13. Frequently asked questions

**Is Bubble safe? It asks for a lot of power.**
Bubble needs system permissions to build and govern the bubble, which no ordinary app can get, so it borrows Android's `shell` identity through wireless debugging. You grant it once, with a code, on purpose: no app can get that identity without you. Bubble is open source so anyone can check what it does with it. [SECURITY.md](../../SECURITY.md) lists every permission and why.

**Does Google still know anything?**
The phone's Google services can know that a private profile exists and which apps are installed in it. They cannot see when you use them, your data, your accounts or your traffic. See the [privacy model](../privacy-model.md).

**Why not just use Android's normal Private Space?**
A normal Private Space comes with Google Play Services and the Play Store inside, which follow you across apps. The bubble leaves them out from the first boot, filters every app's internet, hides its use from the system log and keeps Google out with a guard.

**Do I need root?**
No. Bubble works on a stock phone with a locked bootloader.

**Do I need a computer?**
No. Pairing happens on the phone itself.

**How do I update Bubble?**
Bubble checks GitHub when you open it, at most once every six hours, and offers **Update** when a newer release is available. You can also use **Settings → Check for updates**. Android may ask you to allow installations from Bubble the first time, then asks you to confirm the update. Your data and pairing are kept; do not uninstall the previous version.

**Does it drain the battery?**
The bubble's apps use what they would use anywhere. Bubble itself only works briefly when you use it; the guard checks once a minute while it is alive.

**Can I use my own VPN?**
Yes, on the normal phone. Inside the bubble the slot is taken by Bubble's firewall.

**Why do I have to pair again after resetting the phone?**
A reset erases the authorization of Bubble's key.

**Why does the Meta space have Google installed? Isn't that the opposite of Bubble?**
Facebook and Instagram crash without it, because of a bug in them. The Meta space keeps Google turned off and apart from the bubble, so the bubble stays Google-free. You do not have to create it.

**Will it work on my phone?**
It is tested on a Pixel 9a with Android 17. Other phones may differ: manufacturers change parts of Android that Bubble relies on, and the bubble is only created on Android 17 for now. Reports from other phones are welcome.

**Is it free?**
Yes, and open source (MIT). You can support it through GitHub Sponsors or a donation.
