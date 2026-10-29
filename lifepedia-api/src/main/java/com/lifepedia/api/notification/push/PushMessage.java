package com.lifepedia.api.notification.push;

/**
 * Conteúdo do push (vai cifrado ao aparelho; o service worker mostra).
 *
 * @param url    tela aberta ao tocar
 * @param tag    notificações com a mesma tag se substituem (ex.: chat de um grupo)
 * @param urgent entrega imediata mesmo com o aparelho em economia de bateria
 */
public record PushMessage(String title, String body, String url, String tag, boolean urgent) {
}
