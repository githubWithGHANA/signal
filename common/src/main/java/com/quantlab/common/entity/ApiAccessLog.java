package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "api_access_log")
@Getter
@Setter
public class ApiAccessLog extends AuditingEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "api_access_log_seq")
    @SequenceGenerator(name = "api_access_log_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name= "machine_id")
    private String machineId;

    @Column(name= "user_agent")
    private String  userAgent;

    @Column(name = "user_id")
    private Long appUserId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "request_api")
    private String requestApi;

    // xts or tr
    @Column(name = "api_type")
    private String apiType;

    @Column(name = "api_request_body")
    private String apiRequestBody;

    @Column(name = "api_access_time")
    private Instant apiAccessTime;

    @Column(name = "request_ip")
    private String requestIp;

    @Column(name = "remarks")
    private String remarks;

}
