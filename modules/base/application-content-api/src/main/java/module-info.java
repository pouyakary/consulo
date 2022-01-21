/**
 * @author VISTALL
 * @since 20/01/2022
 */
module consulo.application.content.api {
  // TODO [VISTALL] obsolete dep
  requires java.desktop;

  requires transitive consulo.application.api;
  requires transitive consulo.virtual.file.system.api;
  requires transitive consulo.base.icon.library;
  requires transitive consulo.configurable.api;
  requires transitive consulo.file.chooser.api;

  requires org.jdom;

  exports consulo.content;
  exports consulo.content.bundle;
  exports consulo.content.bundle.event;
  exports consulo.content.library;
  exports consulo.content.library.ui;
}