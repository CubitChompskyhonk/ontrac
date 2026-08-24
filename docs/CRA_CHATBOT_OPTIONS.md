# CRA AI / chat interface options (for OnTrac)

## Official channels (2026)

| Channel | What it is | Account-specific? | OnTrac posture |
|---------|------------|-------------------|----------------|
| **GenAI chatbot** | 24/7 bot on Canada.ca (widget) | **No** — general CRA info only; do not share personal data | Open official page + **safe prompts** held by OnTrac |
| **Online chat in My Account** | Live CRA agent chat after sign-in | **Yes** — account issues | Only after user has access; still representation rules apply |
| **Phone** | Traditional lines | Yes | Call script + authorization |
| **Skip the Line** page | Hub for digital options | Mixed | Link from guided session |

Official GenAI page: https://www.canada.ca/en/revenue-agency/corporate/contact-information/cra-chatbot.html

## Product rules
- OnTrac never embeds a scraped CRA bot; we **open official CRA surfaces**.
- Safe prompts only (registration, how-to) — never SIN/name/account numbers in GenAI chat.
- Account-specific truth comes from My Account + authorized representation, not the public GenAI widget.
- Accuracy of CRA GenAI is imperfect historically; treat as navigation aid, log outcomes on the case.

## WD4 implementation
Guided session records: access status, years visibility, free-text notes → `case.craGuide` + consent receipt.
