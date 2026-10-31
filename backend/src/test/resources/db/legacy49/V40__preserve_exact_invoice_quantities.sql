ALTER TABLE invoice_line
  ADD COLUMN quantity_numerator NUMERIC,
  ADD COLUMN quantity_denominator NUMERIC CHECK (quantity_denominator > 0),
  ADD CONSTRAINT invoice_line_quantity_fraction_pair
    CHECK ((quantity_numerator IS NULL) = (quantity_denominator IS NULL));
