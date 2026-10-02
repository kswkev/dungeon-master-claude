# Game data

Copy `DUNGEON.DAT` from your own copy of Dungeon Master into this folder:

```
data/DUNGEON.DAT
```

The file is git-ignored and should never be committed.

Atari ST, Amiga and PC files are supported, both compressed and uncompressed.
The loader detects the byte order and compression automatically.

To use a file somewhere else, pass its path as the first argument, or set `-Ddm.dungeon=<path>`.
