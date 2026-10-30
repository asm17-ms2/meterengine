import unittest
from datetime import datetime, timezone
from decimal import Decimal
from fractions import Fraction
from csvdemo.expected import BillableMetricMeta, build_expected, dimension_values_key, rate_amount
from core.model import StoredEvent, parse_rfc3339

TIERS = [{"up_to":100,"unit_price":10},{"up_to":1000,"unit_price":8},{"up_to":None,"unit_price":5}]

class TieredPricingTest(unittest.TestCase):
    def test_volume_uses_exact_fraction_at_a_display_rounded_boundary(self):
        rate = {"model": "volume", "tiers": [{"up_to": "0.333333333333333333", "unit_price": 300}, {"up_to": None, "unit_price": 600}]}
        self.assertEqual(rate_amount(Fraction(1, 3), rate), 200)

    def test_graduated_uses_exact_remainder_beyond_a_display_rounded_boundary(self):
        rate = {"model": "graduated", "tiers": [{"up_to": "0.333333333333333333", "unit_price": 300}, {"up_to": None, "unit_price": "3E20"}]}
        self.assertEqual(rate_amount(Fraction(1, 3), rate), 190)

    def test_graduated_boundaries_and_fractional_quantity(self):
        rate = {"model":"graduated","tiers":TIERS}
        for quantity, expected in [("0",0),("100",1000),("101",1000),("1000",8200),("1500.5",10700)]:
            with self.subTest(quantity=quantity):
                self.assertEqual(rate_amount(Decimal(quantity),rate),expected)

    def test_volume_boundaries_and_fractional_quantity(self):
        rate = {"model":"volume","tiers":TIERS}
        for quantity, expected in [("0",0),("100",1000),("101",800),("1000",8000),("1500.5",7500)]:
            with self.subTest(quantity=quantity):
                self.assertEqual(rate_amount(Decimal(quantity),rate),expected)

    def test_customer_price_overrides_common_then_minimum_and_base_apply(self):
        metric = BillableMetricMeta("tokens","tokens","sum","quantity",price_rates={dimension_values_key({}):{"unit_price":10}},customer_price_rates={"a":{dimension_values_key({}):{"unit_price":3}}})
        event = StoredEvent("evt","a","tokens",parse_rfc3339("2026-08-10T00:00:00Z"),{"quantity":100})
        result = build_expected([event],[metric],"2026-08",["a","b"],{"a":{"base_fee":500,"minimum_amount":1000},"b":{"base_fee":500,"minimum_amount":1000}})
        self.assertEqual(result.customers["a"].amount,1500)
        self.assertEqual(result.customers["b"].amount,0)
        self.assertEqual(result.customers["a"].lines[0].amount,300)

    def test_negative_dimensions_are_clamped_before_minimum(self):
        metric = BillableMetricMeta("tokens","tokens","sum","quantity",unit_prices={dimension_values_key({}):Decimal("10")})
        event = StoredEvent("evt","a","tokens",parse_rfc3339("2026-08-10T00:00:00Z"),{"quantity":-100})
        result = build_expected([event],[metric],"2026-08",["a"],{"a":{"base_fee":500,"minimum_amount":1000}})
        self.assertEqual(result.customers["a"].amount,0)
        self.assertEqual(result.customers["a"].lines[0].priced_quantity,0)

    def test_cutoff_itself_and_future_events_are_not_aggregated(self):
        metric = BillableMetricMeta("tokens","tokens","sum","quantity",unit_prices={dimension_values_key({}):Decimal("10")})
        cutoff = parse_rfc3339("2026-08-10T00:00:00Z")
        events = [StoredEvent(str(index),"a","tokens",parse_rfc3339(timestamp),{"quantity":100}) for index,timestamp in enumerate(["2026-08-09T00:00:00Z","2026-08-10T00:00:00Z","2026-08-11T00:00:00Z"])]
        result = build_expected(events,[metric],"2026-08",["a"],cutoff=cutoff)
        self.assertEqual(result.customers["a"].amount,1000)
