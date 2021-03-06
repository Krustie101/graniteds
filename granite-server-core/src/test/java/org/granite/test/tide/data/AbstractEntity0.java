/**
 *   GRANITE DATA SERVICES
 *   Copyright (C) 2006-2015 GRANITE DATA SERVICES S.A.S.
 *
 *   This file is part of the Granite Data Services Platform.
 *
 *   Granite Data Services is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 *
 *   Granite Data Services is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 *   General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 *   USA, or see <http://www.gnu.org/licenses/>.
 */
package org.granite.test.tide.data;

import java.io.Serializable;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.Version;

import org.granite.tide.data.DataPublishListener;

/**
 * @author Franck WOLFF
 */
@MappedSuperclass
@EntityListeners({AbstractEntity0.AbstractEntityListener.class, DataPublishListener.class})
public abstract class AbstractEntity0 implements Serializable {

	private static final long serialVersionUID = 1L;
	
    public AbstractEntity0() {
    }
    
    public AbstractEntity0(Long id, Long version, String uid) {
    	this.id = id;
    	this.version = version;
    	this.uid = uid;
    }
    

	@Id @GeneratedValue
    private Long id;

    /* "UUID" and "UID" are Oracle reserved keywords -> "ENTITY_UID" */
    @Column(name="ENTITY_UID", unique=true, nullable=false, updatable=false, length=36)
    private String uid;

    @Version
    private Long version;

    
    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }
    
    public String getUid() {
    	return uid;
    }

    @Override
    public boolean equals(Object o) {
        return (o == this || (o instanceof AbstractEntity0 && uid().equals(((AbstractEntity0)o).uid())));
    }

    @Override
    public int hashCode() {
        return uid().hashCode();
    }

    public static class AbstractEntityListener {

        @PrePersist
        public void onPrePersist(AbstractEntity0 abstractEntity) {
            abstractEntity.uid();
        }
    }

    private String uid() {
        if (uid == null)
            uid = UUID.randomUUID().toString();
        return uid;
    }
}
