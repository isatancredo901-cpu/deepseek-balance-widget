#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# DeepSeek 余额监控 - Termux 一键安装脚本
# ============================================================
# 在 Termux 中运行: bash setup.sh
# ============================================================

set -e

echo "=========================================="
echo "  🐋 DeepSeek 余额监控 - Termux 安装"
echo "=========================================="
echo ""

# 1. 更新包管理器
echo "📦 [1/5] 更新包管理器..."
pkg update -y && pkg upgrade -y

# 2. 安装依赖
echo ""
echo "📦 [2/5] 安装 Python 和依赖..."
pkg install -y python termux-api cronie

# 3. 安装 Python 库
echo ""
echo "📦 [3/5] 安装 Python requests 库..."
pip install requests -q

# 4. 创建脚本目录
echo ""
echo "📦 [4/5] 创建脚本目录..."
mkdir -p ~/.shortcuts
mkdir -p ~/.deepseek-widget

# 5. 下载主脚本
echo ""
echo "📦 [5/5] 写入监控脚本..."

cat > ~/.deepseek-widget/check_balance.py << 'PYEOF'
#!/data/data/com.termux/files/usr/bin/python
# ============================================================
# DeepSeek API 余额检查 + 持久通知
# ============================================================
import requests
import json
import os
import sys
import subprocess

CONFIG_FILE = os.path.expanduser("~/.deepseek-widget/config.json")
CACHE_FILE = os.path.expanduser("~/.deepseek-widget/cache.json")
API_URL = "https://api.deepseek.com/user/balance"

# IP 地理位置 API（免费，获取货币符号）
IP_API = "https://ipapi.co/json/"

def load_config():
    """加载 API Key 配置"""
    if not os.path.exists(CONFIG_FILE):
        print("❌ 未找到配置文件")
        print(f"请创建 {CONFIG_FILE}:")
        print('{"api_key": "sk-你的key"}')
        sys.exit(1)
    with open(CONFIG_FILE) as f:
        return json.load(f)

def get_currency_info():
    """根据 IP 获取货币信息"""
    try:
        resp = requests.get(IP_API, timeout=5)
        data = resp.json()
        currency = data.get("currency", "CNY")
        country = data.get("country_name", "中国")
        return currency, country
    except:
        return "CNY", None

def fetch_balance(api_key):
    """查询 DeepSeek 余额"""
    headers = {
        "Accept": "application/json",
        "Authorization": f"Bearer {api_key}"
    }
    resp = requests.get(API_URL, headers=headers, timeout=15)
    resp.raise_for_status()
    return resp.json()

def format_balance(amount, currency):
    """格式化金额"""
    symbols = {"CNY": "¥", "USD": "$", "EUR": "€", "GBP": "£", "JPY": "¥"}
    symbol = symbols.get(currency, currency + " ")
    try:
        return f"{symbol}{float(amount):.2f}"
    except:
        return amount

def send_notification(title, content, alert=False, ongoing=True):
    """发送 Termux 通知"""
    cmd = [
        "termux-notification",
        "--title", title,
        "--content", content,
        "--priority", "high" if alert else "default",
        "--id", "deepseek-balance",
    ]
    if ongoing:
        cmd.append("--ongoing")  # 持久通知，不可划掉
    if alert:
        cmd.extend(["--vibrate", "300"])
    subprocess.run(cmd, capture_output=True)

def main():
    try:
        config = load_config()
        api_key = config.get("api_key", "")

        if not api_key or not api_key.startswith("sk-"):
            send_notification(
                "🐋 DeepSeek 余额",
                "⚠️ API Key 未设置，请运行 setup 配置",
                alert=False
            )
            return

        # 查询余额
        data = fetch_balance(api_key)

        # 解析
        available = data.get("is_available", False)
        bi = data.get("balance_infos", [{}])[0]
        currency = bi.get("currency", "CNY")
        total = float(bi.get("total_balance", 0))
        granted = float(bi.get("granted_balance", 0))
        topped_up = float(bi.get("topped_up_balance", 0))

        total_str = format_balance(total, currency)
        topped_str = format_balance(topped_up, currency)
        granted_str = format_balance(granted, currency)

        # 状态图标
        status_icon = "🟢" if available else "🔴"
        status_text = "可用" if available else "余额不足"

        # 通知标题
        title = f"{status_icon} DeepSeek | {total_str}"

        # 通知内容
        content = f"充值: {topped_str} | 赠金: {granted_str} | {status_text}"

        # 发送持久通知
        send_notification(title, content, alert=not available)

        # 缓存数据
        with open(CACHE_FILE, "w") as f:
            json.dump({
                "total": total,
                "granted": granted,
                "topped_up": topped_up,
                "currency": currency,
                "available": available,
                "timestamp": __import__("time").time()
            }, f)

        print(f"✅ {status_icon} {title}")
        print(f"   {content}")

    except requests.exceptions.RequestException as e:
        # 网络错误 → 尝试缓存
        if os.path.exists(CACHE_FILE):
            with open(CACHE_FILE) as f:
                cache = json.load(f)
            cached_total = format_balance(cache["total"], cache["currency"])
            send_notification(
                f"📦 DeepSeek | {cached_total}",
                f"⚠️ 网络错误，显示缓存数据",
                ongoing=True
            )
            print(f"📦 使用缓存: {cached_total}")
        else:
            send_notification(
                "🐋 DeepSeek 余额",
                "❌ 网络错误，无法查询",
                ongoing=True
            )
            print("❌ 网络错误:", e)

    except Exception as e:
        send_notification(
            "🐋 DeepSeek 余额",
            f"❌ {str(e)[:80]}",
            alert=True,
            ongoing=True
        )
        print("❌ 错误:", e)

if __name__ == "__main__":
    main()
PYEOF

chmod +x ~/.deepseek-widget/check_balance.py

# 创建 Termux:Widget 快捷方式
cat > ~/.shortcuts/🐋_刷新余额 << 'SHEOF'
#!/data/data/com.termux/files/usr/bin/bash
python ~/.deepseek-widget/check_balance.py
SHEOF
chmod +x ~/.shortcuts/🐋_刷新余额

echo ""
echo "=========================================="
echo "  ✅ 安装完成！"
echo "=========================================="
echo ""
echo "🔑 下一步：设置你的 API Key"
echo ""
echo "   方式一（推荐）- 直接运行："
echo "     echo '{\"api_key\":\"sk-你的key\"}' > ~/.deepseek-widget/config.json"
echo ""
echo "   方式二 - 用 nano 编辑："
echo "     nano ~/.deepseek-widget/config.json"
echo ""
echo "📱 添加到桌面："
echo "   1. 长按桌面 → 添加 Termux:Widget 小部件"
echo "   2. 点击小部件中的「🐋_刷新余额」即可手动刷新"
echo ""
echo "🔄 设置自动刷新（每30分钟）："
echo "   crontab -e"
echo "   添加: */30 * * * * python ~/.deepseek-widget/check_balance.py"
echo ""
echo "=========================================="
