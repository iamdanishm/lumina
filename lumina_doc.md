# **Lumina: The Vision-to-Action Bridge**

Project Codename: Lumina  
Hackathon Track: Social Good / Agentic AI  
Core Philosophy: "Don't just describe the world. Change it."

## **1\. Executive Summary**

**Lumina** is the world's first "Physical-to-Digital" Agent. It merges the real-time visual perception of **Lumina** with the OS-level execution capabilities of **Neuro-Nav**.

* **The Problem:** Current assistive tech stops at *description*. A blind user is told "There is a poster for a concert," but is left stranded when they try to act on that information (booking a ticket, calling the number). The "Action Gap" remains.  
* **The Solution:** Lumina sees the world, understands the user's intent, and automatically manipulates the operating system to execute the task. It turns visual input into digital output.  
* **The Winning Angle:** We hit the emotional high note of "Restoring Independence" (Impact) while proving elite technical capability by building an Agent that controls other apps (Technical Depth).

## **2\. The Hybrid Architecture: "Look-Then-Act"**

The biggest risk is technical conflict. Running heavy video streaming (Lumina) and heavy UI tree parsing (Neuro-Nav) simultaneously will crash the app or overheat the phone.  
The Solution: Modal Architecture.  
The app never does both at once. It adheres to a strict state machine.

### **State A: The Watcher (Lumina Mode)**

* **Primary Tech:** Gemini Live API (WebSockets).  
* **Function:** Continuous video streaming. The AI describes the environment ("You are approaching a coffee shop...").  
* **Resource Usage:** High Network / High NPU (Video).  
* **Accessibility Service:** **Passive/Sleeping.** It is not scraping the screen; it is waiting for a signal.

### **The Trigger (The Bridge)**

* **User Command:** "Order me a latte from here."  
* **Process:**  
  1. **Freeze:** Camera stream pauses (saves battery/bandwidth).  
  2. **Capture:** A high-res frame is sent to **Gemini 3 Pro**.  
  3. **Extraction:** Gemini identifies the entity ("Starbucks") and the intent ("Order Latte").  
  4. **Payload:** Gemini returns a JSON Action Payload.  
  5. **Hand-off:** The payload is passed to the Accessibility Service.

### **State B: The Doer (Neuro-Nav Mode)**

* **Primary Tech:** Android Accessibility Services \+ Intents.  
* **Function:** Execution.  
* **Process:**  
  1. **Deep Link First (Safety):** The app attempts to launch the target app directly via URI scheme (starbucks://menu/latte).  
  2. **Accessibility Fallback (The Flex):** If no deep link exists, the Accessibility Service wakes up, finds the "Order" button in the UI tree, and performs a click().  
* **Resource Usage:** High CPU (UI Parsing).

## **3\. The Killer Feature: "Ghost Walker" Mode (Blind Navigation)**

**The Scenario:** User wants to walk to a destination but cannot see the map UI or interpret the route visually.  
The Logic:  
Standard Maps navigation says "Turn left in 50 feet." This is dangerous if there is a hole in the ground 40 feet away. Ghost Walker combines Data Navigation (Maps) with Visual Safety (Lumina).  
**The Workflow (Hackathon Demo Flow):**

1. **Voice Input:** "Take me to the nearest Starbucks."  
2. **Neuro-Nav Action:**  
   * Resolves "Nearest Starbucks" to coordinates.  
   * Constructs a Walking Intent: google.navigation:q=Starbucks\&mode=w.  
   * **CRITICAL HACK:** It launches Maps in "Headless" or "Background" mode (or just listens to the Notifications).  
3. **Lumina Supervision (The "Wow" Factor):**  
   * Lumina reads the Map Data: "Route says turn left."  
   * Lumina watches the Camera: "I see a construction barrier on the left."  
   * **Synthesis Output:** "Maps wants you to turn left, but there is construction. Walk past the orange cones first, then turn."

**Why this wins:** It solves the "Last Meter" problem that GPS cannot solve. It proves the AI is *reasoning*, not just repeating GPS data.

## **4\. Technical Implementation Strategy**

### **Phase 1: The "Lumina" Frontend (Vision)**

* **Stack:** Kotlin \+ CameraX \+ OkHttp (WebSockets).  
* **Key Feature:** **Live API Streaming.**  
  * Do not send every frame. Send 2-3 frames per second to manage latency.  
  * Use **Gemini 2.5 Flash** for the continuous description (fast, cheap).  
  * Switch to **Gemini 3 Pro** only when the user asks a complex question or gives a command.

### **Phase 2: The "Neuro-Nav" Backend (Action)**

* **Stack:** Android AccessibilityService.  
* **The "Cheat Code" for Hackathons:**  
  * **Do not** try to build a universal agent that can use *any* app. That is an impossible 2-year project.  
  * **Do** build a "Happy Path" for 3 specific demos.  
    * **Demo A:** Uber (Ride Sharing).  
    * **Demo B:** OpenTable (Reservations).  
    * **Demo C:** Maps (Ghost Walker Mode).  
  * Hardcode the viewId or contentDescription lookups for these specific apps if the generic AI parser fails. **Reliability \> Purity.**

### **Phase 3: The Integration**

* **Data Structure:**  
  {  
    "intent": "BOOK\_RIDE",  
    "parameters": {  
      "destination": "123 Main St",  
      "app\_preference": "uber"  
    },  
    "confidence": 0.95  
  }

* **Safety Layer:** Always ask for confirmation before spending money. "I'm about to book an Uber to 123 Main St. Price is approx $15. Confirm?"

## **5\. Scoring Analysis & Judge Psychology**

| Criteria | Strategy |
| :---- | :---- |
| **Impact (40%)** | **The Narrative:** "We are not just building an app; we are building an OS for the blind." Focus on the *freedom* the user gains. The demo video should show a user completing a task entirely hands-free. |
| **Technical (30%)** | **The Flex:** Show the code for the WebSocket handler (Live API) AND the Accessibility Node parser. This proves you are a full-stack mobile engineer, not just an API wrapper dev. |
| **Innovation (20%)** | **The Pivot:** Most entries will be chatbots. Yours is an *Agent*. Emphasize the **"Physical-to-Digital Bridge"**—this concept is fresh and specifically leverages Gemini's multimodal strengths. |
| **Presentation (10%)** | **The Demo:** |
| 1\. **Start:** User walking down street (Lumina describing). |  |
| 2\. **Conflict:** Sees a concert poster. Wants tickets. |  |
| 3\. **Climax:** "Get me tickets." \-\> Phone opens browser \-\> Fills seats \-\> Checkout. |  |
| 4\. **Resolution:** User smiles. "Done." |  |

## **6\. Risk Mitigation (The "Do or Die" Rules)**

1. **The "Demo Effect" Rule:** Live demos fail. Pre-record the screen capture of the "Action" phase. If the Accessibility Service glitches on stage, you can seamlessly cut to the video or narrated explanation.  
2. **The "Internet" Rule:** Venues have bad WiFi.  
   * **Lumina:** Use a lower resolution for the stream if bandwidth drops.  
   * **Neuro-Nav:** Ensure your "Deep Link" logic works offline (preparing the Intent) even if the target app fails to load data.  
3. **The "Privacy" Rule:** Judges might ask about security permissions.  
   * **Answer:** "This is a proof-of-concept. In production, we would use Android's AssistContent API and strictly scoped permissions, but for the hackathon, we are demonstrating the maximum theoretical capability of an AI Agent."

## **7\. Development Timeline (Hackathon Sprint)**

* **Day 1:** **Core Lumina.** Get CameraX streaming frames to Gemini Live API. Get audio response back.  
* **Day 2:** **Core Neuro-Nav.** Build the Accessibility Service skeleton. specific logic for *one* app (e.g., Calendar). Test the "Poster to Calendar" flow.  
* **Day 3:** **The Bridge.** Connect the two. Refine the prompt engineering to ensure Gemini outputs clean JSON for the Agent.  
* **Day 4:** **Polish & Video.** Record the demo. Write the submission text. Add "Fake" UI polish (animations, sound effects) to make the "thinking" phase look cool.

## **8\. Useful Prompts for Development**

**System Prompt for the Action Planner (Gemini 3 Pro):**  
"You are an Action Agent. You receive an image and a user command. Your job is NOT to chat. Your job is to extract entities and formulate a JSON payload for an Android Intent or Accessibility Action.  
Output Format: JSON Only.  
Keys: target\_app (e.g., 'com.google.android.calendar'), action\_type (e.g., 'insert\_event'), details (key-value pairs of data)."  
**Prompt for generating the "Fake" UI (Gemini Nano):**  
"Generate a futuristic 'Scanning' overlay for an Android camera preview. Neon blue lines, HUD style, transparent background. SVG format."