import re


# 문자열, 범위, 매처 묶음을 통째로 한 토큰으로 읽어 그 안의 단어를 지표 이름으로 잘못 보지 않는다.
TOKEN = re.compile(r'"(?:[^"\\]|\\.)*"|\[[^\]]*\]|\{(?:[^}"]|"(?:[^"\\]|\\.)*")*\}|[a-zA-Z_:][\w:]*|\d[\w.]*|\$\w+|\S')
NAME = re.compile(r"[a-zA-Z_:][\w:]*")
GROUPING = {"by", "without", "on", "ignoring", "group_left", "group_right"}
NOT_METRICS = GROUPING | {"offset", "bool", "and", "or", "unless", "sum", "min", "max", "avg", "count", "topk",
                          "bottomk", "quantile", "stddev", "stdvar", "count_values", "group"}


def selectors(expr):
    """PromQL 식의 지표 선택자마다 (지표 이름, 매처 묶음)을 돌려준다. 매처가 없는 선택자는 빈 문자열이다."""
    tokens = TOKEN.findall(expr)
    found, index = [], 0
    while index < len(tokens):
        token = tokens[index]
        following = tokens[index + 1] if index + 1 < len(tokens) else ""
        if token in GROUPING and following == "(":
            index = tokens.index(")", index)
        elif token.startswith("{"):
            found.append(("", token))
        elif NAME.fullmatch(token) and token not in NOT_METRICS and following != "(":
            matchers = following if following.startswith("{") else ""
            found.append((token, matchers))
            index += 1 if matchers else 0
        index += 1
    return found
