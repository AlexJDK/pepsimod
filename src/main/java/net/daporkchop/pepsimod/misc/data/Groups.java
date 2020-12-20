/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2016-2020 DaPorkchop_
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * Any persons and/or organizations using this software must include the above copyright notice and this permission notice,
 * provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package net.daporkchop.pepsimod.misc.data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * @author DaPorkchop_
 */
public class Groups implements AutoCloseable {
    protected Map<UUID, Group> playerToGroup = Collections.emptyMap();
    protected Collection<Group> groups = Collections.emptyList();

    public void addGroup(Group group) {
        if (this.groups.isEmpty()) {
            this.groups = new HashSet<>();
        }
        this.groups.add(group);
        group.members.forEach(uuid -> this.addPlayerMapping(uuid, group));
    }

    public void addPlayerMapping(UUID uuid, Group group) {
        if (this.playerToGroup.isEmpty()) {
            this.playerToGroup = new HashMap<>();
        }
        this.playerToGroup.put(uuid, group);
    }

    @Override
    public void close() {
        this.groups.forEach(Group::close);

        this.playerToGroup.clear();
        this.groups.clear();
    }
}
