Remote Compose Player for Linux (x86-64)
========================================

A desktop viewer for Remote Compose (.rc) documents, drawn by the Compose Multiplatform player
(rc-player-compose) on the JVM. A Java runtime is bundled; nothing else needs to be installed.

Run it
------

From this directory:

    ./bin/remote-compose-player                 # empty window
    ./bin/remote-compose-player path/to/doc.rc  # open a document

or install RemoteComposePlayer-linux-amd64.deb (from the same release) with

    sudo apt install ./RemoteComposePlayer-linux-amd64.deb

which puts the launcher at /opt/remote-compose-player/bin/remote-compose-player and adds a
"Remote Compose Player" entry to the Development menu.

In the window
-------------

- File > Open (Ctrl+O), or drop a .rc file onto the window.
- File > Reload (Ctrl+R) re-reads the file from disk, restarting its animations.
- File > Open in Inspector (Ctrl+I), or "Inspect", opens the document in the Remote Compose
  binary inspector (https://camaelon.github.io/remotecompose-experiments/inspector/) in your
  browser: operations, layout tree, variables and byte allocation. The document travels inside the
  link's fragment, so it is not uploaded anywhere. It works for documents the player refused too.
- "Theme" cycles System / Light / Dark; "Fill window" / "Document size" switches between letting
  the document fill the window and giving it the size its header declares.
- The status bar lists any operations the CMP desktop player knows but does not draw. A document
  the player cannot draw at all is refused with the reason instead of being shown half-drawn.

Headless
--------

    ./bin/remote-compose-player --render-png in.rc out.png [width height]

draws one frame without a display and writes a PNG.

    ./bin/remote-compose-player --inspect doc.rc        # open it in the web inspector
    ./bin/remote-compose-player --inspector-url doc.rc  # print the inspector link
