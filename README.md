# Oxygen Voice Assistant — README (Hinglish)

Ye ek **starter skeleton** hai, production-ready polished app nahi. Isse aap chalu
kar sakte hain, test kar sakte hain, aur aage improve kar sakte hain.

## Kya-kya bana hua hai

- "hey oxygen" bolne par wake-up, "sleep oxygen" bolne par sleep (`WakeWordService.kt`)
- Dynamic-Island-jaisa floating pill jo top mein aata/jaata hai, pulse animation ke saath (`OverlayService.kt`)
- Torch on/off, app open karna (naam bol kar), time batana, basic reminder hook (`CommandProcessor.kt`)
- Bina API key ke internet search — DuckDuckGo + Wikipedia (`SearchHelper.kt`)
- Har command se pehle ek random joke
- Boot hone par apne aap start ho jaana (`BootReceiver.kt`)
- Launcher-icon ko baad mein hide karne ka structure (`activity-alias` manifest mein)

## Kya nahi bana / limitations (zaroor padhein)

1. **Wake-word engine simple hai.** Ye Android ke built-in `SpeechRecognizer` ko
   restart-loop mein chala raha hai, isliye:
   - Har ~1 second recognizer restart hota hai (chhota gap ban sakta hai)
   - Internet chahiye (Google recognizer ke liye) - agar aap 100% offline chahte
     hain to **Vosk** (offline, free, no API key) library integrate karni hogi.
     Steps README ke end mein diye hain.
2. **Notification hata nahi sakte.** Android 8+ har foreground service (jo mic
   use kare) ke liye permanent notification dikhana **zaroori** banata hai. Icon
   home-screen se hata sakte hain, notification nahi.
3. **Data on/off app khud nahi kar sakta** (Android security policy) — sirf
   Settings screen khol sakta hai, user ko tap karna padega.
4. Ye code **compile karke test nahi kiya gaya** is sandbox mein (isme Android
   SDK/network available nahi hai), isliye pehli build mein 1-2 chhoti error
   aana normal hai — neeche diye commands se error dikh jaayegi, fix karna easy hoga.

---

## APK banane ka step-by-step process (BINA Android Studio)

### Step 1: Java aur command-line tools install karo
```bash
# Linux/Termux/WSL:
sudo apt update
sudo apt install openjdk-17-jdk -y
java -version   # confirm karo 17 dikh raha ho
```

### Step 2: Android command-line SDK download karo
1. https://developer.android.com/studio#command-tools se "Command line tools only" zip download karo
2. Extract karke aise folder structure banao:
```
~/android-sdk/cmdline-tools/latest/   (yahan bin, lib waghera ho)
```
3. Environment variables set karo:
```bash
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

### Step 3: Zaroori SDK packages install karo
```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
sdkmanager --licenses   # sab licenses "y" karke accept karo
```

### Step 4: Project folder mein jaao aur build karo
```bash
cd OxygenAssistant
chmod +x gradlew   # agar gradlew file nahi hai, "gradle wrapper" command se pehle banao
./gradlew assembleDebug
```
Build success hone par APK yahan milega:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Step 5: Phone mein install karo
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
(Phone mein Settings > Developer Options > USB debugging ON karna hoga)

### Agar `gradlew` file nahi hai
Sirf `gradle` (system-installed) se pehli baar wrapper generate karo:
```bash
sudo apt install gradle -y
gradle wrapper --gradle-version 8.5
```
Fir upar wale `./gradlew assembleDebug` waale step se continue karo.

### Bina laptop ke, sirf phone se banana ho to
**Termux** app (F-Droid se) install karke usi ke andar upar wale saare commands
chala sakte hain — Termux ek Linux terminal hai jo Android phone ke andar hi
chalta hai, isse aapko computer ki zaroorat nahi padegi. Bas RAM kam ho to build
thodi slow hogi.

---

## Home-screen se icon hide karna (setup ke baad)

Pehle app ko normal tarike se ek baar open karke saari permissions de dijiye
(Mic, Camera, Overlay) aur "Start" bata dijiye. Uske baad icon hide karne ke
liye terminal se (adb ke through):

```bash
adb shell pm disable-user --user 0 com.oxygen.assistant/.LauncherAlias
```

Isse home-screen se icon gayab ho jaayega lekin `WakeWordService` background
mein chalta rahega (notification dikhta rahega — Android isse hatane nahi
deta). Icon wapas laane ke liye:
```bash
adb shell pm enable com.oxygen.assistant/.LauncherAlias
```

---

## Vosk se real offline wake-word (advanced, agla step)

Jab basic version chal jaaye aur aap isko battery-friendly, fully-offline
banana chahein:

1. https://alphacephei.com/vosk/models se Hindi/English chhota model
   (`vosk-model-small-en-in` ya similar) download karo
2. Model ko `app/src/main/assets/model/` mein daalo
3. `implementation 'com.alphacephei:vosk-android:0.3.47'` dependency add karo
4. `SpeechRecognizer` (Android built-in) ki jagah Vosk ka `SpeechService` use
   karo jo grammar-restricted continuous listening deta hai — isse "hey oxygen"
   ko keyword-spot kar sakte ho bina Google/internet ke.

Ye thoda advanced kaam hai; basic version pehle test kar lena better rahega.

---

## Testing checklist
- [ ] App open karke saari 3 permissions di
- [ ] "hey oxygen" bola — island (pill) top pe aana chahiye + "Ji boliye" sunna chahiye
- [ ] "torch on" bola — flashlight jalni chahiye
- [ ] "open whatsapp" bola — WhatsApp khulni chahiye (agar installed hai)
- [ ] "sleep oxygen" bola — island gayab ho jaana chahiye
- [ ] Kuch general sawaal poocha — internet se jawab aana chahiye
