package com.ordinalssync.orccash.inscriptiondata.model;

import lombok.Data;

import java.util.List;

@Data
public class InscriptionContent {
    private String p;
    private String op;
    private String tick;
    private String amt;
    private String max;
    private String lim;
    private String dec;
    private String name;
    private String ug;
    private String id;
    private String wp;
    private String v;
    private String msg;
    private String n;
    private List<String> to;
    private String price;
    private String expire;
    private String seller;
    private String buyer;
    private String vote;
    private String quo;
    private String pass;
    private String toAddress;
    private String burnTo;
    private List<String> from;
}
