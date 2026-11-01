---
name: smart-forward
description: Catch a notification from one app and forward its content to a contact in another, with a one-line summary the recipient can act on. Useful for sharing OTPs, package tracking updates, news alerts, or "did you see this" moments.
allowed-tools: list_recent_notifications notification_action_click launch_app read_window_tree find_node click_node set_text take_screenshot global_action
---

# Smart-forward

Pick up a notification from app A and forward its substance to a contact in app B. Add a one-line summary on top so the recipient doesn't have to interpret the raw text.

## When to use

- "Forward this email to <person>"
- "Send <person> the tracking number that just came in"
- "Tell <person> the OTP I just got" (be careful — see Don't section)
- A notification arrives that matches a user-defined forwarding rule (workflow trigger)

## Steps

1. **Pick the source notification.** `list_recent_notifications` — find the entry the user is referring to (or the most recent if "this" / "that" was the demonstrative). Note the sender, the visible body text, and the source package.
2. **Decide if the body needs more.** Notifications often truncate to 80-120 chars. If the user wants the full thing forwarded, open the app: `notification_action_click` with the entry's primary action, then `read_window_tree` on the resulting screen, pull the full message body as text.
3. **Compose the summary.** One sentence. Examples:
   - Tracking notif: "Your package is out for delivery — expected by 6 PM today."
   - News alert: "Reuters: <headline>."
   - Email: "<sender> sent you <subject> — <one-line gist>."
   - OTP: NEVER FORWARD — see Don't.
4. **Choose the destination.** Open the app the user requested, locate the confirmed contact, open the thread, and send via `set_text` plus the send button. If the recipient is ambiguous or the requested app is unavailable, stop and ask instead of choosing another channel.
5. **Confirm.** Reply to the user with "Forwarded to <person>" + a one-line preview.
6. **Return home.** `global_action(action = "home")`.

## Tools used

- `list_recent_notifications`, `notification_action_click`
- `launch_app`, `read_window_tree`, `find_node`, `click_node`, `set_text`
- `take_screenshot` (debugging only)
- `global_action`

## Failure modes

- **Source app doesn't expose body in the accessibility tree.** Some banking / 2FA apps deliberately hide content. Forward the notification text only and mention "the app hides the rest — open it on your phone for the full thing".
- **Destination contact ambiguous.** "Send Anna the article" — if there are two matching contacts, ask the user which one. Don't guess.
- **Source notification already dismissed.** It still lives in the recent ring buffer for a few minutes — work from there, but the cached preview may be truncated.

## Don't

- **Never forward OTPs / verification codes / password-reset links** unless the user explicitly types the OTP themselves and asks you to relay it. The whole reason these things exist is that they shouldn't travel further than they have to. If the user asks "send Bob my OTP", refuse with "I won't auto-forward verification codes — they're meant for you only".
- **Only forward content to the recipient the user explicitly authorized.** Do not infer consent from a matching name.
- Don't summarise + forward the same content twice — if the user already replied "forward it" once, don't re-fire on the next "yes".
