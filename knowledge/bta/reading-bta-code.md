# Reading BTA's code (Mac only)

Loom's `genSources` fails for BTA. Decompile just the classes you need with Vineflower from the Gradle cache (needs one earlier BTA build on the machine):

```bash
J=~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/8.0.1/minecraft-merged-deobf-8.0.1.jar
V=$(find ~/.gradle/caches -name "vineflower-*.jar" | head -1)
mkdir -p /tmp/bta-classes /tmp/bta-src && cd /tmp/bta-classes
unzip -qo "$J" 'org/useless/dragonfly/*' 'net/minecraft/client/render/block/model/*'
java -jar "$V" /tmp/bta-classes /tmp/bta-src
```

- Signatures only: `javap -cp "$J" net.minecraft.core.util.helper.Side`.
- HalpLibe: `find ~/.gradle/caches/modules-2 -name "halplibe-6.2.0+8.0.1.jar"`.
- Searching class files for a name: `grep -rla 'SomeClass' <unzipped dir>` (`-a`, they are binary).
- Write what you find into the matching `knowledge/bta/*.md` file. Never paste large chunks of decompiled code into the repo.
