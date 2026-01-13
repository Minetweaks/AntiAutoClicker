# AntiAutoClicker

A Paper plugin designed to detect and combat automated clicking tools like KillAura through behavioral analysis and captcha verification.

## Features

- **Attack Pattern Analysis**: Monitors player attack patterns to detect automation
  - Tracks attack rate and timing consistency
  - Analyzes cooldown precision (KillAura attacks at precise intervals)
  - Calculates suspicion scores based on multiple factors

- **GUI Captcha System**: Interactive inventory-based challenges
  - Players must drag a glowing redstone to a chest among decoy items
  - Configurable interval (default: 30 minutes)
  - 2-minute response timeout
  - Detects cheating (attacking/moving while captcha is open)

- **Tiered Punishment System**: Escalating consequences for repeat offenders
  - First offense: Kick
  - Second offense: 1-hour tempban
  - Third offense: 24-hour ban
  - Fourth+ offense: Permanent ban
  - All tiers fully configurable with custom commands

- **Persistent Storage**: SQLite or MySQL support
  - Tracks player failure counts across restarts
  - Stores captcha history and attack statistics

- **Discord Integration**: Sends detailed reports via webhook when players fail verification
  - Player name, UUID, and masked IP
  - Location and game mode
  - Attack statistics and suspicion score
  - Beautifully formatted embeds

- **Admin Commands**: Full control via Cloud Commands
  - `/aac reload` - Reload configuration
  - `/aac captcha <player>` - Manually issue captcha
  - `/aac stats <player>` - View player attack stats
  - `/aac reset <player>` - Clear player data
  - `/aac list` - List monitored players
  - `/aac test webhook` - Test Discord webhook

## How It Works

The plugin analyzes attack patterns to detect KillAura-like behavior:

1. **Timing Consistency**: Humans have variable click timing; automation is more consistent
2. **Cooldown Precision**: KillAura attacks at 90-98% of attack cooldown, which is detectable
3. **Sustained Attack Rate**: High attack rates maintained over time indicate automation

When a player reaches the attack threshold (configurable), they receive a GUI captcha challenge. They must drag the glowing redstone item to the chest among many decoy items. If they fail, timeout, or are detected attacking/moving with the GUI open, punishments escalate based on their total failure count.

## Configuration

```yaml
# Discord webhook URL for reporting failed captchas
discord-webhook-url: "https://discord.com/api/webhooks/..."

# How often to issue captcha challenges (in minutes)
captcha-interval-minutes: 30

# How long a player has to respond to a captcha (in seconds)
captcha-timeout-seconds: 120

# Minimum number of entity attacks required before captcha is issued
minimum-attacks-for-captcha: 100

# Storage type: "sqlite" or "mysql"
storage:
  type: sqlite
```

## Permissions

- `antiautoclick.admin` - Access to all admin commands
- `antiautoclick.bypass` - Bypass captcha checks
- `antiautoclick.notify` - Receive notifications when players fail captcha

## Building

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## Requirements

- Paper 1.21.4+ or Folia 1.21.4+
- Java 21+

## Dependencies

- Cloud Commands (shaded)
- Gson (shaded)
