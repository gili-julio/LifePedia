package com.lifepedia.api.price;

import java.math.BigDecimal;

/** Página de produto do Buscapé reduzida ao que o parser lê. */
public final class PriceFixtures {

    public static final String PRODUCT_URL = "https://www.buscape.com.br/fone-de-ouvido/headphone-jbl-tune-520bt";

    private PriceFixtures() {
    }

    /** @param kabum preço à vista da KaBuM! (a mais barata quando abaixo de 224,90) */
    public static String product(BigDecimal kabum) {
        String data = """
                {"props":{"initialReduxState":{
                  "products":{"product":{"product":{"name":"Headphone JBL Tune 520BT"}}},
                  "offers":{"countOfStores":7,"offerList":[
                    {"id":"1514100977","sellerName":"KaBuM!","merchantId":"16108","price":%s,"totalPrice":259.9,
                     "imageUrl":"https://image.zst.com.br/foto_235_235.jpg"},
                    {"id":"1113072261","sellerName":"Amazon","merchantId":"1582","price":224.9,"totalPrice":224.9},
                    {"id":"1078744912","sellerName":"Amazon","merchantId":"9385","price":228.9,"totalPrice":228.9}]},
                  "priceHistory":{"priceHistory":{"12483700":{"days":[
                    {"date":"2026-10-09","price":180.41,"merchantId":1582},
                    {"date":"2026-10-10","price":169.15,"merchantId":16108}]}}}}}}
                """.formatted(kabum.toPlainString());
        return "<html><head><meta property=\"og:image\" content=\"https://image.zst.com.br/foto_235_235.jpg\"></head><body>"
                + "<script id=\"__NEXT_DATA__\" type=\"application/json\">" + data + "</script></body></html>";
    }
}
