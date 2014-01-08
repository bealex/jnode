package org.jnode.httpd.dto;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "httpd_pointrequest")
public class PointRequest {
	@DatabaseField(columnName = "id", generatedId = true)
	private Long id;
	@DatabaseField(columnName = "ftn_address", canBeNull = false)
	private String ftnAddress;
	@DatabaseField(columnName = "station_name", canBeNull = false)
	private String name;
	@DatabaseField(columnName = "email", canBeNull = false)
	private String email;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getFtnAddress() {
		return ftnAddress;
	}

	public void setFtnAddress(String ftnAddress) {
		this.ftnAddress = ftnAddress;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

}