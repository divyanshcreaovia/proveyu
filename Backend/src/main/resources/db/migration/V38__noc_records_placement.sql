-- Approving a NOC records the off-campus offer in the college's offers (accepted), so the student becomes PLACED and the offer counts
-- in the dashboard, reports and placement policy like any campus offer. The NOC keeps the id of that offer.
ALTER TABLE off_campus_noc_requests ADD COLUMN college_offer_id CHAR(36) NULL AFTER noc_generated;
