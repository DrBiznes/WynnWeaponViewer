# Wynncraft Weapon Bigger

![WynnWeaponBiggerBanner](https://github.com/user-attachments/assets/443188e9-20e3-4aa0-a001-60ad3e3daa22)


I make your weapon big, duh. A fork of [Big Items, Duh](https://modrinth.com/mod/big-items-duh) designed to only show Wynncraft gear, now on 1.21.11. Made for my modpack [World of Wynncraft](https://modrinth.com/modpack/world-of-wynncraft)

![promo](https://github.com/user-attachments/assets/4002859a-f832-4c8d-a317-c423ae58d11b)

## What's Up

- Zoom in on Wynncraft gear in your inventory
- Two display modes: a **floating item** with its name, roll % and badges under it, or a Wynncraft-style **item card** with the item's IDs, roll bars, powders, rerolls and Nori/Wynnpool scales
- Perfect rolls get a rainbow card frame, defective ones get static
- Share any item: Wynntils' Share Item key links it in chat, **Ctrl+C copies its item card as an image**
- Appear and idle animations, configurable zoom scale and position
- Toggle gear zoom functionality with a keybind
- Uses Wynntils mod to detect Wynncraft weapons and gear
- Tooltips always display on top of zoomed weapons
- Configurable settings via Mod Menu or the config file, Cloth Config and Mod Menu are optional

## See It

Floating item mode (the default): the item big, with its name, overall roll and badges under it.

![floating](https://github.com/user-attachments/assets/4435b280-5459-45b3-a69d-b25fc2ca8cea)


Item card mode: the same item in a Wynncraft-style card with every ID and roll. This is also what Ctrl+C copies.

![card](https://github.com/user-attachments/assets/4f9ce3c6-72d6-400f-a949-555ff743acd2)

## Installation

1. Make sure you have Fabric Loader and Fabric API installed
2. Download and install version 4.2.18 or later of Wynntils, and optionally Cloth Config and Mod Menu for the config screen
3. Download the latest version of Wynncraft Weapon Bigger from the versions page
4. Place the downloaded .jar file in your Minecraft mods folder

## Configuration

Every setting lives in `config/wynnweaponbigger.json`, so WWB works with no other mods. To edit settings in game:

1. Install [Cloth Config](https://modrinth.com/mod/cloth-config) (and [Mod Menu](https://modrinth.com/mod/modmenu) for a "Configure" button in the mod list).
2. Open the settings with `/wwb config`, or from Mods -> "Wynn Weapon Bigger" -> "Configure".
3. Pick the display mode (floating item, the default, or item card), size, side and offsets, what the card shows, animations, Ctrl+C sharing, and which items get the preview.

Without Cloth Config, `/wwb config` shows where the file is. Edit it, then run `/wwb reload`. A broken file won't crash the game: bad values fall back to their defaults.

## Usage

- Hover over a Wynncraft weapon in your inventory to see it zoomed in.
- The zoomed view will disappear when you move your cursor away from the weapon.
- Use the configured keybind (default: period key) to toggle the zoom functionality on/off. (Currently, hotkeys don't work in inventories. This is a Minecraft bug)
- Share the hovered item: press Wynntils' Share Item key (default F5) to link it in chat, or Ctrl+C to copy its item card as an image. The small print under the preview shows your current keys.
- Use /wwb for a list of commands

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.4 or higher
- Fabric API
- Wynntils 4.2.18 or higher
- Cloth Config and Mod Menu (optional, they add the config screen)

## Go Ham

- I don't know nothing about java so if you wanna fork this and fix this up go ham.
- Feel free to use this in your modpack
- Not made by or affiliated with Wynncraft or Wynntils

## Acknowledgments

- Thanks to Siphalor for the original mod Big Items, Duh
- Thanks to iMeeTake for ItemZoomer, which the 1.0 rendering is based on
- Thanks to the Wynntils team, this mod would know nothing about your gear without them
- Thanks to the Wynncraft team
- Thanks to shedaniel for Cloth Config
- Thanks to IgbarvonSquid

## Support and My Mods
Please report any bugs or feature suggestions on the Github Issues page, I'll be updating this frequently with community feedback and ideas! You can also [join my discord](https://discord.gg/jqFF64rXZZ) if you need direct support, or want to stay updated with all of my mods.
### Check out all my projects!
>   [World of Wynncraft Modpack](https://modrinth.com/modpack/world-of-wynncraft)

>   [WynnTitles](https://modrinth.com/mod/wynntitles)

>   [WynnWanderer](https://modrinth.com/mod/wynnwanderer)

>   [WynnPhoton](https://modrinth.com/mod/wynnphoton)

>   [WynnVista](https://modrinth.com/mod/wynnvista)

>   [Wynn Weapon Bigger](https://modrinth.com/mod/wynnweaponbigger)

>   [Nimble ReWynnded](https://modrinth.com/mod/nimble-rewynnded)

>   [Class Keybind Profiles](https://modrinth.com/mod/class-keybind-profiles)

>   [WynnBubbles](https://modrinth.com/mod/wynnbubbles)

>   [WynnLODGrabber](https://modrinth.com/mod/wynnlodgrabber)
