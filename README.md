# gwr
Блокчейн-решение для команды GWR

Установите git, Java 8 и sbt.
Clone репозиторий через git clone (address):

```
https://github.com/mir-one/gwr.git
```

Отредактируйте параметры параметры конфигуратора генезиса сети src/test/resources/genesis.example.conf, для примера используйте это:

```
genesis-generator
{
  network-type: "L"  #Байт идентификатор вашей сети
  initial-balance: 10000000000000000  #Общее число актива (здесь указано 8 десятичных знаков )
  base-target: 153722867
  average-block-delay: 60s #Интервал генерации блоков
  timestamp: 1500635421931 #закомментируйте этот параметр, чтобы использовать время запуска сети

  # seed text -> share
  # the sum of shares should be <= initial-balance
  distributions
  {
    "foo0": 10000000000000
  }
}
```

Запустите генерацию genesis, блока вашей сети, используя:

```
sbt "test:runMain tools.GenesisBlockGenerator src/test/resources/genesis.example.conf"
```

Результат будет примерно такой
```
Addresses:
(0):
 Seed text:           foo0
 Seed:                3csAfH
 Account seed:        58zgAnBg775J6NKd4qVtfeX3m5TBMeizHNY9STvm2N87
 Private account key: FYLXp1ecxQ6WCPD4axTotHU9RVfPCBLfSeKx1XSCyvdT
 Public account key:  GbGEY3XVc2ohdv6hQBukVKSTQyqP8rjQ8Kigkj6bL57S
 Account address:     3JfE6tjeT7PnpuDQKxiVNLn4TJUFhuMaaT5

Settings:
genesis {
  average-block-delay: 60000ms
  initial-base-target: 153722867
  timestamp: 1500635421931
  block-timestamp: 1500635421931
  signature: "4xpkFL6TdaEwqZnDcuMVSei77rR5S8EpsEr3dkFMNoDCtxxhBVQCbzkeGwKLdyT5zcPumpNnqgybb3qeLV5QtEKv"
  initial-balance: 10000000000000000
  transactions = [{recipient: "3JfE6tjeT7PnpuDQKxiVNLn4TJUFhuMaaT5", amount: 10000000000000}]}
  ```

Создайте network.conf (илидругое имя) и вставьте это содержимое:

```
# Network node settings
network
{
  # data storage folder
  directory=/tmp/custom

  logging-level = DEBUG

  blockchain
  {
    type: CUSTOM
    custom
    {
      address-scheme-character: "L"
      # various parameters of network consensus
      functionality {
        feature-check-blocks-period = 30
        blocks-for-feature-activation = 25
        allow-temporary-negative-until: 0
        allow-invalid-payment-transactions-by-timestamp: 0
        require-sorted-transactions-after: 0
        generation-balance-depth-from-50-to-1000-after-height: 0
        minimal-generating-balance-after: 0
        allow-transactions-from-future-until: 0
        allow-unissued-assets-until: 0
        require-payment-unique-id-after: 0
        allow-invalid-reissue-in-same-block-until-timestamp: 0
        allow-multiple-lease-cancel-transaction-until-timestamp: 0
        reset-effective-balances-at-height: 1
        allow-leased-balance-transfer-until: 0
        block-version-3-after: 0
        pre-activated-features = {
          2 = 0
        }
        # ...
      }
      genesis
      {
        average-block-delay: 60s
        initial-base-target: 153722867
        timestamp: 1500635421931
        block-timestamp: 1500635421931
        signature: "4xpkFL6TdaEwqZnDcuMVSei77rR5S8EpsEr3dkFMNoDCtxxhBVQCbzkeGwKLdyT5zcPumpNnqgybb3qeLV5QtEKv"
        initial-balance: 10000000000000000
        transactions = [{recipient: "3JfE6tjeT7PnpuDQKxiVNLn4TJUFhuMaaT5", amount: 10000000000000}]
      }
    }
  }

  network
  {
    bind-address = "0.0.0.0"
    port = 6860
    known-peers = []
    node-name = "L custom node 1"
    declared-address = "127.0.0.1:6860"
  }

  wallet
  {
    password = "password"
    seed = "3csAfH"
  }

  rest-api
  {
    enable = yes
    bind-address = "0.0.0.0"
    port = 6861
    api-key-hash = "H6nsiifwYKYEx6YzYD7woP1XCn72RVvx6tC1zjjLXqsu"
  }

  miner
  {
    interval-after-last-block-then-generation-is-allowed = 999d
    quorum = 0
  }
}
```

Запустите сеть

```
sbt 'run network.conf'
