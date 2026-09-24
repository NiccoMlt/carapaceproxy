/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */
package org.carapaceproxy.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Build information of the running Carapace, read once at class load.
 * <p>
 * The source is {@code META-INF/carapace.version.properties}, written at build time by git-commit-id-maven-plugin.
 * When the file is missing (classes not produced by Maven) the version falls back to the {@code Implementation-Version}
 * manifest entry, then to {@code dev}; the commit falls back to {@code unknown}.
 */
public final class CarapaceVersion {

    /** Project version, e.g. {@code 2.4.0-SNAPSHOT}; {@code dev} when no build information is available. */
    public static final String VERSION;

    /** Abbreviated commit hash the build was made from; {@code unknown} when not available. */
    public static final String COMMIT;

    static {
        final Properties props = new Properties();
        try (InputStream in = CarapaceVersion.class.getResourceAsStream("/META-INF/carapace.version.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // build info is best effort: a broken file must never stop the server
        }
        final Package pkg = CarapaceVersion.class.getPackage();
        final String manifestVersion = pkg == null ? null : pkg.getImplementationVersion();
        VERSION = props.getProperty("git.build.version", manifestVersion == null ? "dev" : manifestVersion);
        COMMIT = props.getProperty("git.commit.id.abbrev", "unknown");
    }

    private CarapaceVersion() {
    }
}
