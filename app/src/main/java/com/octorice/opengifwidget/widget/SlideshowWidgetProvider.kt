package com.octorice.opengifwidget.widget

/**
 * The "OpenGIFWidget Slideshow" entry in the launcher's widget list.
 *
 * Android lists one widget per receiver, so the slideshow needs its own class, but all the work is
 * the same as for a normal widget and lives in [GifWidgetProvider]. The only thing this class adds
 * is its name: [GifWidgetProvider.isSlideshow] asks the launcher which class a widget belongs to.
 */
class SlideshowWidgetProvider : GifWidgetProvider()
