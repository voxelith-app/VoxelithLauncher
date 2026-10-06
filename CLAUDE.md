# Voxelith Launcher (celular)

Leia este arquivo no início de toda sessão. Sempre que o dono tomar uma decisão nova, registre aqui (seção "Decisões").

Launcher de Minecraft Java para Android do Voxelith. É um fork do MojoLauncher (que por sua vez é fork do PojavLauncher), mantido em voxelith-app/VoxelithLauncher. O launcher de PC fica em voxelith-app/code e tem um CLAUDE.md com as regras gerais do projeto (marca, cores, idioma, estilo). As mesmas regras valem aqui.

Foco: otimização para celular e integração com o botão "Exportar para celular" do launcher de PC, que gera um .mrpack leve. O MojoLauncher já importa .mrpack.

## Donos
- GitHub: euzane e joaoooomartins.
- Commits saem como `joaoooomartins <shadowonerb11@gmail.com>`, passando o autor no commit. Não alterar git user.name nem user.email.

## Licença (LGPL-3.0)
- Manter os avisos de copyright, o LICENSE e os créditos do MojoLauncher e do PojavLauncher.
- O código das alterações tem que ficar público (este repositório).
- Marcar que o código foi modificado (README e histórico do git).
- Componentes de terceiros mantêm as próprias licenças (OpenJDK GPLv2, Authlib-Injector AGPL-3.0, Mesa, GL4ES, LWJGL, SDL e outros). Não remover os avisos.
- A licença não cobre nome nem logo: usar só a marca Voxelith, nada do Mojo, do Pojav ou da Mojang.

## Marca e cores
- Nome: Voxelith. Ícone vem de voxelith-app/code, arquivo branding/voxelith-icon.png. Não redesenhar.
- Ciano: padrão #14C8EC, claro #3DE0FF (fundo escuro), escuro #0098B8 (fundo claro). Fundo escuro de marca #0B1A22, claro #E6FAFF.
- Verde, vermelho e amarelo só como cor de estado.

## Idioma
- pt-BR é o padrão, en-US é reserva. Textos novos sempre em português.

## Estrutura
- app_pojavlauncher: o app (Java, pacote net.kdt.pojavlaunch, namespace git.artdeell.mojo). O namespace e os pacotes Java foram mantidos para não mexer em centenas de arquivos.
- applicationId: app.voxelith.launcher (debug: app.voxelith.launcher.debug).
- Build: `./gradlew :app_pojavlauncher:assembleFullDebug`. O dono só tem celular durante a semana: o APK sai pelo GitHub Actions (aba Actions, artefato app-debug).

## Estilo
- Commits curtos, em minúsculas, no imperativo, em português.
- Sem assinatura de IA em commits, PRs, README ou código.
- README enxuto, sem marketing e sem emoji.

## Pendências
- Ely.by: o login usa o client ID do Mojo (mojolauncher2). Registrar um nosso no Ely.by ou desativar.
- Builds de release e Google Play usam as chaves do Mojo (mojo_*.jks). Criar chaves próprias antes de publicar.
- O workflow baixa LTW e Mesa de repositórios do MojoLauncher. Conferir se o APK funciona sem eles ou se precisa fazer fork desses também.

## Decisões
- 2026-10-06: fork criado em voxelith-app/VoxelithLauncher a partir de MojoLauncher/MojoLauncher (branch v3_openjdk).
