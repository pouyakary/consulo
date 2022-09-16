package consulo.ide.impl.builtInServer.impl.net.http;

import consulo.application.Application;
import consulo.builtinWebServer.http.HttpRequestHandler;
import consulo.builtinWebServer.http.HttpResponse;
import consulo.builtinWebServer.http.util.HttpRequestUtil;
import consulo.ide.impl.builtInServer.impl.net.websocket.WebSocketHandler;
import consulo.logging.Logger;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.function.ThrowableFunction;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

@ChannelHandler.Sharable
final class DelegatingHttpRequestHandler extends DelegatingHttpRequestHandlerBase {
  private static final AttributeKey<HttpRequestHandler> PREV_HANDLER = AttributeKey.valueOf("DelegatingHttpRequestHandler.handler");

  @Override
  protected HttpResponse process(ChannelHandlerContext context, FullHttpRequest request, QueryStringDecoder urlDecoder) throws Exception {
    consulo.builtinWebServer.http.HttpRequest httpRequest = new HttpRequestImpl(request, urlDecoder, context);
    ThrowableFunction<HttpRequestHandler, HttpResponse, IOException> checkAndProcess = httpRequestHandler -> {
      if (httpRequestHandler.isSupported(httpRequest)) {
        if (!HttpRequestUtil.isWriteFromBrowserWithoutOrigin(httpRequest)) {
          if (httpRequestHandler.isAccessible(httpRequest)) {
            return httpRequestHandler.process(httpRequest);
          }
        }
      }
      return null;
    };


    Attribute<HttpRequestHandler> prevHandlerAttribute = context.channel().attr(PREV_HANDLER);
    HttpRequestHandler connectedHandler = prevHandlerAttribute.get();
    if (connectedHandler != null) {
      HttpResponse temp = checkAndProcess.apply(connectedHandler);
      if (temp != null) {
        return temp;
      }
      // prev cached connectedHandler is not suitable for this request, so, let's find it again
      prevHandlerAttribute.set(null);
    }

    HttpHeaders headers = request.headers();
    if ("Upgrade".equalsIgnoreCase(headers.get(HttpHeaderNames.CONNECTION)) && "WebSocket".equalsIgnoreCase(headers.get(HttpHeaderNames.UPGRADE))) {

      // adding new handler to the existing pipeline to handle WebSocket Messages
      context.pipeline().replace(this, "websocketHandler", new WebSocketHandler());
      // do the Handshake to upgrade connection from HTTP to WebSocket protocol
      handleHandshake(context, request);
      return HttpResponse.ok();
    }

    for (HttpRequestHandler handler : HttpRequestHandler.EP_NAME.getExtensionList()) {
      try {
        HttpResponse temp = checkAndProcess.apply(handler);
        if (temp != null) {
          prevHandlerAttribute.set(handler);
          return temp;
        }
      }
      catch (Throwable e) {
        Logger.getInstance(BuiltInServer.class).error(e);
      }
    }

    if (urlDecoder.path().equals("/favicon.ico")) {
      Icon icon = TargetAWT.to(Application.get().getIcon());
      BufferedImage image = UIUtil.createImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      icon.paintIcon(null, image.getGraphics(), 0, 0);
      byte[] icoBytes = Imaging.writeImageToBytes(image, ImageFormats.ICO, null);
      return HttpResponse.ok("image/vnd.microsoft.icon", icoBytes);
    }

    return null;
  }

  private void handleHandshake(ChannelHandlerContext ctx, HttpRequest req) {
    WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketURL(req), null, true);
    WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);
    if (handshaker == null) {
      WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
    }
    else {
      handshaker.handshake(ctx.channel(), req);
    }
  }

  private String getWebSocketURL(HttpRequest req) {
    return "ws://" + req.headers().get("Host") + req.uri();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    try {
      ctx.channel().attr(PREV_HANDLER).set(null);
    }
    finally {
      super.exceptionCaught(ctx, cause);
    }
  }
}
