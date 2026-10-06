<p align="center">
	<img src="./app_pojavlauncher/src/main/assets/pojavlauncher.png" width="150" alt="Voxelith" />
</p>

# Voxelith Launcher

Launcher para jogar Minecraft: Java Edition no Android. É a versão de celular do [Voxelith](https://github.com/voxelith-app/code), o launcher de PC, e abre os modpacks .mrpack que ele exporta.

Roda quase todas as versões do Minecraft e aceita Forge, Fabric, Quilt e NeoForge.

## Como baixar

Toda alteração gera um APK na aba [Actions](https://github.com/voxelith-app/VoxelithLauncher/actions). Abra a build mais recente e baixe o artefato `voxelith-debug`.

## Como compilar

```
./gradlew :app_pojavlauncher:assembleFullDebug
```

No Windows, use `.\gradlew.bat` e crie links de `mojoexec`, `sdl` e `glfw` dentro de `app_pojavlauncher/src/main/jni/`.

## Créditos

Mantido por [euzane](https://github.com/euzane) e [joaoooomartins](https://github.com/joaoooomartins).

O Voxelith Launcher é uma versão modificada do [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher), que é baseado no [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher). As alterações feitas pelo Voxelith começam em outubro de 2026 e estão no histórico do git deste repositório. Não tem ligação com o MojoLauncher, o PojavLauncher, a Mojang ou a Microsoft.

## Licença

Distribuído sob a [GNU LGPLv3](./LICENSE), a mesma licença do MojoLauncher e do PojavLauncher.

## Componentes de terceiros

- [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher): [GNU LGPLv3](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/LICENSE)
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher): [GNU LGPLv3](https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/LICENSE)
- Android Support Libraries: [Apache 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt)
- [Holy GL4ES](https://github.com/artdeell/gl4es_extra_extra/): [MIT](https://github.com/ptitSeb/gl4es/blob/master/LICENSE)
- [OpenJDK](https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u): [GNU GPLv2 com Classpath Exception](https://openjdk.java.net/legal/gplv2+ce.html)
- [GLFW](https://github.com/MojoLauncher/glfw): [zlib](https://github.com/MojoLauncher/glfw/blob/glfw34/LICENSE.md)
- [SDL](https://github.com/MojoLauncher/MojoSDL): [zlib](https://github.com/MojoLauncher/MojoSDL/blob/main/LICENSE.txt)
- [LWJGL2-GLFW](https://github.com/MojoLauncher/lwjgl2-glfw): BSD 3-Clause
- [LWJGL3](https://github.com/LWJGL/lwjgl3): [BSD 3-Clause](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md)
- [mojoexec](https://github.com/MojoLauncher/mojoexec): [MIT](https://github.com/MojoLauncher/mojoexec/blob/master/LICENSE)
- [Mesa 3D](https://gitlab.freedesktop.org/mesa/mesa): [MIT](https://docs.mesa3d.org/license.html)
- [pro-grade](https://github.com/pro-grade/pro-grade): [Apache 2.0](https://github.com/pro-grade/pro-grade/blob/master/LICENSE.txt)
- [bhook](https://github.com/bytedance/bhook): [MIT](https://github.com/bytedance/bhook/blob/main/LICENSE)
- [Authlib-Injector](https://github.com/yushijinhun/authlib-injector): [AGPL-3.0](https://github.com/yushijinhun/authlib-injector/blob/develop/LICENSE)
- [OpenAL Soft](https://github.com/kcat/openal-soft/): [GNU LGPL](https://github.com/kcat/openal-soft/blob/master/COPYING) e [PFFFT modificado](https://github.com/kcat/openal-soft/blob/master/LICENSE-pffft)
- [oboe](https://github.com/google/oboe): [Apache 2.0](https://github.com/google/oboe/blob/main/LICENSE)
- [Lucide](https://lucide.dev): [ISC](https://lucide.dev/license)
- Avatares do Minecraft fornecidos pelo [Mineskin](https://mineskin.eu/).
