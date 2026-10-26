# Volume Flutuante (Android)

Um app que coloca um botão flutuante sobre os outros apps para controlar o volume do celular.

## Recursos
- Botão flutuante que você arrasta para qualquer lugar da tela (a posição fica salva)
- Toque no botão para abrir o painel: **−**, barra deslizante de volume, **+**, **mudo** e **recolher**
- O painel se recolhe sozinho depois de 5 segundos sem uso
- **Transparência ajustável** (de 0% a 90%) — a mudança aparece na hora
- Tamanho do botão ajustável (32–80 dp)
- Escolha qual volume controlar: mídia, toque, notificações, alarme ou chamada
- Opção de mostrar ou não o painel de volume do próprio Android
- Notificação fixa com o botão **Parar**

## Instalação
1. Baixe o `VolumeFlutuante.apk` para o celular e abra o arquivo (permita "instalar apps desconhecidos" se o Android pedir).
2. Abra o app e toque em **Conceder permissão** → ative "Mostrar sobre outros apps".
3. Volte e toque em **Mostrar botão flutuante**.

Requer Android 8.0 ou mais recente.

## Compilar
```bash
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```
