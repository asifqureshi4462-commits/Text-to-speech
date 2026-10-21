package com.asifqureshi.aivoicestudio.network;

import java.io.IOException;

public class ServerNotConfiguredException extends IOException {
    public ServerNotConfiguredException() {
        super("server_not_configured");
    }
}
