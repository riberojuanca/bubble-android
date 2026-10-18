# 2. Install and set up

## Install Bubble

1. Download the latest APK from the [Releases page](https://github.com/riberojuanca/bubble-android/releases).
2. Open it and allow your browser or file manager to install apps when Android asks.
3. Open **Bubble**.

> From 2027 Android will require apps installed outside stores to come from a verified developer. See [developer verification](../developer-verification.md) for what that means for Bubble.

## Step 1 of 2: pair Bubble

Bubble needs Android's `shell` identity to build the bubble. It gets it through **wireless debugging**, which you allow once with a pairing code. After this step Bubble turns wireless debugging on only while it works and off again.

1. **Turn on Developer options**: Settings → About phone → tap **Build number** seven times. Enter your PIN if asked.
2. Make sure you are on **Wi-Fi**. In Bubble, tap **Pair**. This starts the pairing notification and opens Developer options.
3. In Developer options, turn on **Wireless debugging**. If Android asks *"Allow wireless debugging on this network?"*, tick **Always allow on this network** and accept.
4. Tap **Wireless debugging** (the name, not the switch) and open **Pair device with pairing code**. A 6-digit code appears. **Leave this dialog open.**
5. Keep the dialog open and stay in Settings. Open Bubble's notification and tap **Pair**.
6. Enter the **6-digit code** there and send the reply.
7. When the notification shows *Paired*, return to Bubble.

If the code is rejected (*"The code does not match or the pairing dialog closed"*), open the pairing dialog again (the code changes each time) and repeat steps 4 to 6.

## Step 2 of 2: create the bubble

Tap **Create the bubble**. It takes about a minute. Bubble:

1. Creates a Private Space with only the system parts Android needs to run it, leaving out every Google service.
2. Watches the system while it starts; if anything crashes, it removes the new profile at once.
3. Installs **F-Droid** inside, so you can install apps, and a **keyboard without internet** (your phone's Gboard, or HeliBoard if you have no Gboard).
4. Sets up the bubble's firewall (only F-Droid has internet at first), the smart lock and the silencing of system events.

If you already have a Private Space, Bubble does not touch it and asks:

- **It has Google services**: it cannot be the bubble. **Delete and create** removes it, with its apps and data, and creates the bubble.
- **It has no Google services**: **Use this one** keeps it as it is, with its apps and data, or **Delete and create** replaces it.

When it finishes, Bubble shows *The bubble is ready*. Tap **Start**.

## Enter the bubble for the first time

Open the app drawer and scroll to the bottom: there is a **Private** section. Tap it and unlock it with your screen lock. You will see F-Droid there. That is the bubble.

## After a reboot

You do not need to pair again. After a reboot Bubble restores its protection by itself the next time it has Wi-Fi. The bubble starts **locked**; open it from the Private section as usual.
