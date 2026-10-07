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
- Build: `./gradlew :app_pojavlauncher:assembleFullDebug`. O dono só tem celular durante a semana: o APK sai pelo GitHub Actions (aba Actions, artefato voxelith-debug).
- Cada push na v3_openjdk publica o APK na release fixa `voxelith-nightly` (download sem login: releases/download/voxelith-nightly/voxelith.apk). O corpo da release tem `commit: <sha>`, que o app compara com o commit embutido no build (string voxelith_commit) para avisar que tem versão nova.

## Interface
- Visual igual ao launcher de PC: superfícies escuras (#16181C, #1D1F23, #27292E, #34363C, #42444A), cantos arredondados (8/12/16dp), botão principal em ciano com texto preto, demais botões cinza.
- Tudo feito com shapes XML e vetores, sem imagens nem bibliotecas novas, para o app continuar leve.
- Ícones são do Lucide (licença ISC), os mesmos do launcher de PC, convertidos para vector drawable mantendo os nomes antigos (ic_px_*).

## Abas
- Barra inferior com Início, Mods, Skins, Servidores e Hosting (LauncherActivity). Abas não empilham: voltar sempre leva ao Início. A aba inicial é configurável (startTab).
- Hosting: divulga a BlackHosting (blackhosting.com.br). Planos, preços, cupom e links ficam em hosting/blackhosting.json. O app usa a cópia em assets e atualiza pela versão do GitHub (raw da v3_openjdk), então dá para mudar preço sem lançar APK novo.
- Servidores: junta os servers.dat de todas as instâncias com a lista do launcher (voxelith_servers.json), faz ping e entra direto (quick play passado ao processo do jogo por voxelith_quickplay.txt). A tela inicial mostra os 3 últimos jogados.
- Skins: Microsoft pela API oficial (enviar PNG, clássico/fino, voltar ao padrão). Ely.by só mostra e abre o site. Conta local salva em voxelith_skins e copia para CustomSkinLoader/LocalSkin/skins de cada instância; precisa do mod CustomSkinLoader (botão instala).
- Configurações em Geral, Jogo, Launcher, Avançado e Sobre (versão, commit, procurar atualização, código e licenças).

## Mods, resource packs e shaders
- Código em app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/mods e nos fragments InstanceContentFragment e ContentBrowserFragment.
- Fonte única: API pública do Modrinth v2 (a mesma do launcher de PC). Filtra pelo loader e pela versão da instância e instala dependências obrigatórias.
- Mod desativado = arquivo com sufixo .disabled, igual ao launcher de PC.
- Zalith Launcher 2 (GPL-3.0, Kotlin/Compose) é só referência de funções. Não copiar código dele, para o app continuar LGPL e leve.

## Estilo
- Commits curtos, em minúsculas, no imperativo, em português.
- Sem assinatura de IA em commits, PRs, README ou código.
- README enxuto, sem marketing e sem emoji.

## Pendências
- Ely.by: o login usa o client ID e o client secret do Mojo (mojolauncher2), em ElyByBackgroundLogin.java e ElyByLoginFragment.java. Registrar um app nosso no Ely.by (redirect internalredirect://complete) e trocar. Não desativar: o dono quer suporte a conta pirata.
- Builds de release e Google Play usam as chaves do Mojo (mojo_*.jks). Criar chaves próprias antes de publicar.
- O workflow baixa LTW e Mesa de repositórios do MojoLauncher. Conferir se o APK funciona sem eles ou se precisa fazer fork desses também.

- F3 com texto minúsculo só na 26.3. Na 1.21.1 com Fabric, Sodium e LTW (Adreno 610) o F3 fica normal. Suspeita: LTW com a 26.x. Investigar nos issues do MojoLauncher/LTW.
- A linha "Display" do F3 mostra "(MojoLauncher)": é o GL_VENDOR da biblioteca LTW, que vem pronta do repositório MojoLauncher/LTW. Trocar exige fork e build próprio do LTW.

- Hosting: conferir preços, cupom e contatos (Discord/WhatsApp) da BlackHosting em hosting/blackhosting.json.

## Decisões
- 2026-10-06: fork criado em voxelith-app/VoxelithLauncher a partir de MojoLauncher/MojoLauncher (branch v3_openjdk).
- 2026-10-06: interface refeita no estilo do launcher de PC, mantendo todas as funções.
- 2026-10-06: gerenciador de mods feito com código próprio em Java. Zalith e launcher de PC só como referência. Só Modrinth, sem CurseForge.
- 2026-10-06: botão Otimizar (tela de Mods) instala pacote de desempenho (Sodium/Embeddium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, Entity Culling, MoreCulling, Dynamic FPS) e aplica opções leves no options.txt. Alvo: Samsung A05s (Snapdragon 680, Adreno 610, 4/6 GB). Renderizador padrão continua GL4ES; com Sodium o app troca sozinho para LTW.
- 2026-10-07: suporte a conta pirata mantido (conta local e Ely.by). Conta local não exige conta Microsoft.
- 2026-10-07: release fixa no GitHub, aviso de atualização no app e tela de detalhes do mod (descrição, até 4 imagens reduzidas e novidades da versão).
- 2026-10-07: navegação por barra inferior. Novas abas Skins, Servidores e Hosting. Configurações reorganizadas como no launcher de PC.
- 2026-10-07: aba Hosting divulga a BlackHosting. Preços tirados de buscas porque o site não abriu daqui: o dono precisa conferir.
- 2026-10-07: Otimizar também instala BadOptimizations e desliga o desfoque dos menus.
- 2026-10-07: teste num Galaxy A23 4G (Snapdragon 680 e Adreno 610, o mesmo chip do A05s), 1.21.1 Fabric com botão Otimizar: Sodium 0.6.13 rodando no LTW, distância 6, perto de 60 FPS (p99.5 em 49), 1,1 GB de memória com 38% em uso.
