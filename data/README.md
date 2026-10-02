# Game data

Copy these files from your own copy of Dungeon Master into this folder:

```
data/DUNGEON.DAT
data/GRAPHICS.DAT
```

Both files are git-ignored and should never be committed.

- **DUNGEON.DAT:** Atari ST, Amiga and PC files are supported, both compressed and uncompressed. The loader detects the byte order and compression automatically.
- **GRAPHICS.DAT:** must be the PC version. It is optional. Without it the game uses placeholder art.

To use files somewhere else, pass the DUNGEON.DAT path as the first argument (or set `-Ddm.dungeon=<path>`), and set `-Ddm.graphics=<path>` for GRAPHICS.DAT.
