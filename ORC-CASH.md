### 1、ORC-CASH简介

​        ORC-CASH是一种基于Ordinals协议的代币标准，目的是最好的适配UTXO安全模型。它可以看作是ORC-20标准的简化版本，更容易实施和更广泛地采用所有基于UTXO的区块链，大多数规则集的灵感来自BRC-20和ORC-20的标准，包括社区成员提出的OIP。目前ORC-CASH仍是一个实验性的代币协议，它需要在每个单独的区块链上构建多个索引器，以方便使用该标准部署的任何代币。无法保证采用该标准的任何代币都具有财务/实用价值或未来的钱包支持。(具体详情请见官方文档https://docs.orc.cash/)

### 2、ORC-CASH基本操作

#### 1、Deploy

铭文示例

```
{ 
  "p": "orc-cash",
  "tick": "OSH",
  "id": "1",
  "op": "deploy",
  "max": "21000000",
  "lim": "1000",
  "dec": "18"
}
```

此操作可用来部署不同规格的代币

#### 2、Mint

铭文示例

```
{ 
  "p": "orc-cash",
  "tick": "OSH",
  "id": "1",
  "op": "mint",
  "amt": "1000"
}
```

此操作可用于铸造一定数量的已部署的代币

#### 3、Send

铭文示例

```
{ 
  "p": "orc-cash",
  "tick": "OSH",
  "id": "1",
  "op": "send",
  "amt": "1000"
}
```

此操作可用于将一定量的代币余额发送到一个钱包地址

#### 4、Upgrade

铭文示例

```
{ 
  "p": "orc-cash",
  "tick": "OSH",
  "id": "1",
  "op": "upgrade",
  "max": "21000000",
  "lim": "500",
  "dec": "18",
  "ug": "false",
  "v": "2",
  "msg": "Limit halving, final upgrade!"
}
```

此操作可用于将代币升级到不同规格。

#### 5、Sell

铭文示例

```
{ 
  "p": "orc-cash",
  "tick": "OSH",
  "id": "1",
  "op": "sell",
  "amt": "100000",
  "lim": "10000",
  "price": "1000",
  "expire": "100",
  "seller": "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f",
  "buyer": "anyone"
}
```

此操作可用于从发送者的钱包中提供代币积分，从而出售给单个、多个或任何钱包地址提供等量的代币。有效买家只需在销售订单激活时将本地代币（如BTC）发送到卖家地址，即可有效购买销售中的代币积分。

#### 6、Airdrop

铭文示例

```
{ 
  "p": "orc-cash",
  "tick": "OSH",
  "id": "1",
  "op": "airdrop",
  "amt": "100000",
  "lim": "10000",
  "to": 
  ["bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f",
  "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f",
  "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f",
  "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f",
  "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f",
  "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f",
  "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f",
  "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f",
  "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f",
  "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f"]
}
```

此操作可用于花费发送者钱包中的代币积分，以向多个钱包地址发送等量的代币

#### 7、Propose

铭文示例

```
{ 
  "p": "orc-cash",
  "tick": "OSH",
  "id": "1",
  "op": "propose",
  "v": "1",  
  "quo": "30",
  "pass": "16",
  "expire": "100",
  "msg": "Proposing the Voting System for ORC-CASH Protocol"
}
```

此操作可以让任何已部署的代币的持有者发起一个投票提案

#### 8、Vote 

铭文示例

```
{ 
  "p": "orc-cash",
  "tick": "OSH",
  "id": "1",
  "op": "vote",
  "v": "1",
  "amt": "1000",
  "vote": "yes",
  "msg": "Agrees to the proposing of the Voting System for ORC-CASH Protocol"
}
```

此操作可用于让一个已部署的代币的持有者对该代币中正在进行的投票提案进行投票

#### 9、Lock

铭文示例

```
{ 
  "p": "orc-cash",
  "tick": "OSH",
  "id": "1",
  "op": "lock",
  "amt": "1000",
  "expire": "100",
  "to": "bc1pwhxpeuvauge29rrhjeyjq6y3489tw86nssl0s8lpvgn2exhntyjquctx6f"
}
```

此操作可用于锁定给定块数的代币积分，并在锁定到期后将积分发送到指定的钱包地址。

#### 10、Burn

铭文示例

```
{ 
  "p": "orc-cash",
  "tick": "OSH",
  "id": "1",
  "op": "burn",
  "amt": "1000"
}
```

此操作可用于燃烧代币积分并减少代币的总供应量，或者燃烧到支持ORC-CASH协议的另一个链。

### 3、代码注意事项

1、com.ordinalssync.orccash.inscriptiondata.task包下的类中的ordiUrl需修改为用户自己使用的rpc地址；

2、com.ordinalssync.orccash.inscriptiondata包下的InscriptionDataDealService类中的ordiUrl需修改为用户自己的rpc地址；

3、resources包下的application.properties配置文件中需自行添加数据库信息和redis信息