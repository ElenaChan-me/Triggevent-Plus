package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/alliance/thaleia.ts をもとに移植
@CalloutRepo(name = "Thaleia", duty = KnownDuty.Thaleia)
public class Thaleia extends AutoChildEventHandler implements FilteredEventHandler {

	// 3人同時に付くタンク向け範囲攻撃のマーカーを集めきるまでの待ち時間
	private static final int CLEAVE_COLLECT_MS = 500;
	private static final int CLEAVE_TARGETS = 3;
	// ヒエログリフィカ併用時は回転後に左右の判定が来るまで待つ
	private static final int BANK_HIEROGLYPHIKA_DELAY_MS = 12_000;
	private static final int EULOGIA_FORMS = 3;

	private XivState state;

	public Thaleia(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Thaleia);
	}

	private boolean soaringMinuet;
	private final List<String> eulogiaForms = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		soaringMinuet = false;
		eulogiaForms.clear();
	}

	// 共通

	private static boolean isTankCleaveMarker(HeadMarkerEvent hm) {
		// ライトン、アロー（小・大）、エウロギアのサンビーム
		return hm.markerIdMatches(0x1D7, 0x158, 0x1F4);
	}

	private final ModifiableCallout<HeadMarkerEvent> tankCleaveOnYou = new ModifiableCallout<>("Rhyton / The Arrow / Sunbeam (On You)", "Tank Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> tankCleaves = new ModifiableCallout<>("Rhyton / The Arrow / Sunbeam (Not on You)", "Tank Cleaves");
	private final RepeatSuppressor tankCleaveSupp = new RepeatSuppressor(Duration.ofSeconds(3));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tankCleaveSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> isTankCleaveMarker(hm) && tankCleaveSupp.check(hm),
			(e1, s) -> {
				List<HeadMarkerEvent> markers = new ArrayList<>();
				markers.add(e1);
				markers.addAll(s.waitEventsUntil(CLEAVE_TARGETS - 1,
						HeadMarkerEvent.class, Thaleia::isTankCleaveMarker,
						BaseEvent.class, e -> e1.getEffectiveTimeSince().toMillis() > CLEAVE_COLLECT_MS));
				HeadMarkerEvent mine = markers.stream().filter(hm -> hm.getTarget().isThePlayer()).findFirst().orElse(null);
				if (mine != null) {
					s.updateCall(tankCleaveOnYou, mine);
				}
				else {
					s.updateCall(tankCleaves, e1);
				}
			});

	private final ModifiableCallout<HeadMarkerEvent> thousandfoldBack = new ModifiableCallout<>("Thousandfold Thrust (Back)", "Back");
	private final ModifiableCallout<HeadMarkerEvent> thousandfoldFront = new ModifiableCallout<>("Thousandfold Thrust (Front)", "Front");
	private final ModifiableCallout<HeadMarkerEvent> thousandfoldLeft = new ModifiableCallout<>("Thousandfold Thrust (Left)", "Left");
	private final ModifiableCallout<HeadMarkerEvent> thousandfoldRight = new ModifiableCallout<>("Thousandfold Thrust (Right)", "Right");

	@HandleEvents
	public void thousandfoldThrust(EventContext context, HeadMarkerEvent event) {
		// ボス自身に付くマーカーで向きが決まる
		if (event.getTarget().isThePlayer()) {
			return;
		}
		ModifiableCallout<HeadMarkerEvent> call;
		if (event.markerIdMatches(0x182)) {
			call = thousandfoldBack;
		}
		else if (event.markerIdMatches(0x183)) {
			call = thousandfoldFront;
		}
		else if (event.markerIdMatches(0x184)) {
			call = thousandfoldLeft;
		}
		else if (event.markerIdMatches(0x185)) {
			call = thousandfoldRight;
		}
		else {
			return;
		}
		context.accept(call.getModified(event));
	}

	// サリャク

	@NpcCastCallout(0x88D1)
	private final ModifiableCallout<AbilityCastStart> katarraktes = ModifiableCallout.durationBasedCall("Katarraktes", "Raidwide + Bleed");

	@NpcCastCallout(0x88D8)
	private final ModifiableCallout<AbilityCastStart> thlipsis = ModifiableCallout.durationBasedCall("Thlipsis", "Stack on {event.target}");

	@NpcCastCallout(0x88D2)
	private final ModifiableCallout<AbilityCastStart> leftBank = ModifiableCallout.durationBasedCall("Left Bank", "Go Right");

	@NpcCastCallout(0x88D3)
	private final ModifiableCallout<AbilityCastStart> rightBank = ModifiableCallout.durationBasedCall("Right Bank", "Go Left");

	private final ModifiableCallout<AbilityCastStart> leftBankHieroglyphika = new ModifiableCallout<>("Left Bank (Hieroglyphika)", "Go Right");
	private final ModifiableCallout<AbilityCastStart> rightBankHieroglyphika = new ModifiableCallout<>("Right Bank (Hieroglyphika)", "Go Left");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> bankHieroglyphikaSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8C2C, 0x8C2D),
			(e1, s) -> {
				s.waitMs(BANK_HIEROGLYPHIKA_DELAY_MS);
				s.updateCall(e1.abilityIdMatches(0x8C2C) ? leftBankHieroglyphika : rightBankHieroglyphika, e1);
			});

	@NpcCastCallout(value = 0x88D5, onYou = true)
	private final ModifiableCallout<AbilityCastStart> thaliakHydroptosis = ModifiableCallout.durationBasedCall("Hydroptosis", "Spread");

	@NpcCastCallout(0x88C4)
	private final ModifiableCallout<AbilityCastStart> rheognosis = ModifiableCallout.durationBasedCall("Rheognosis", "Look for Knockback Position");

	@NpcCastCallout(0x88C5)
	private final ModifiableCallout<AbilityCastStart> rheognosisPetrine = ModifiableCallout.durationBasedCall("Rheognosis Petrine", "Knockback + Rolling Stones");

	@NpcCastCallout(0x88CF)
	private final ModifiableCallout<AbilityCastStart> thaliakHieroglyphika = ModifiableCallout.durationBasedCall("Hieroglyphika (Thaliak)", "Go to Rotated Safe Zone");

	// リムレーン

	@NpcCastCallout(0x880B)
	private final ModifiableCallout<AbilityCastStart> tempest = ModifiableCallout.durationBasedCall("Tempest", "Raidwide");

	@NpcCastCallout(0x880D)
	private final ModifiableCallout<AbilityCastStart> seafoamSpiral = ModifiableCallout.durationBasedCall("Seafoam Spiral", "Get Under");

	@NpcCastCallout(0x880C)
	private final ModifiableCallout<AbilityCastStart> windRose = ModifiableCallout.durationBasedCall("Wind Rose", "Out");

	@NpcCastCallout(0x8811)
	private final ModifiableCallout<AbilityCastStart> navigatorsTrident = ModifiableCallout.durationBasedCall("Navigator's Trident", "Knockback");

	@NpcCastCallout(0x8812)
	private final ModifiableCallout<AbilityCastStart> surgingWave = ModifiableCallout.durationBasedCall("Surging Wave", "Far Knockback");

	@NpcCastCallout(0x8851)
	private final ModifiableCallout<AbilityCastStart> leftStrait = ModifiableCallout.durationBasedCall("Left Strait", "Go Right");

	@NpcCastCallout(0x8852)
	private final ModifiableCallout<AbilityCastStart> rightStrait = ModifiableCallout.durationBasedCall("Right Strait", "Go Left");

	@NpcCastCallout({0x8819, 0x8834})
	private final ModifiableCallout<AbilityCastStart> deepDive = ModifiableCallout.durationBasedCall("Deep Dive", "Stack on {event.target}");

	@NpcCastCallout(value = 0x881F, onYou = true)
	private final ModifiableCallout<AbilityCastStart> stormwinds = ModifiableCallout.durationBasedCall("Stormwinds", "Spread");

	@NpcCastCallout(0x881A)
	private final ModifiableCallout<AbilityCastStart> llymlaenTridents = new ModifiableCallout<>("Torrential Tridents (Llymlaen)", "Last Trident, then First Trident", 12_000);

	@NpcCastCallout(0x8824)
	private final ModifiableCallout<AbilityCastStart> godsbane = ModifiableCallout.durationBasedCall("Godsbane", "Raidwide + Bleed");

	// オシュオン

	@NpcCastCallout({0x8999, 0x899A})
	private final ModifiableCallout<AbilityCastStart> suddenDownpour = ModifiableCallout.durationBasedCall("Sudden Downpour", "Raidwide");

	@NpcCastCallout(0x898E)
	private final ModifiableCallout<AbilityCastStart> trekShot = ModifiableCallout.durationBasedCall("Trek Shot", "Side of Arrow, Boss Doesn't Move");

	private final ModifiableCallout<AbilityCastStart> reproduceOne = ModifiableCallout.durationBasedCall("Reproduce (First)", "Side of Arrow");
	private final ModifiableCallout<AbilityCastStart> reproduceTwo = ModifiableCallout.durationBasedCall("Reproduce (After Soaring Minuet)", "Corner Between Arrows");

	@HandleEvents
	public void reproduce(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8989)) {
			context.accept((soaringMinuet ? reproduceTwo : reproduceOne).getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> flintedFoehn = new ModifiableCallout<>("Flinted Foehn", "6x Stack on {event.target}");

	@PlayerHeadmarker(0x13C)
	private final ModifiableCallout<HeadMarkerEvent> flintedFoehnOnYou = new ModifiableCallout<>("Flinted Foehn (On You)", "6x Stack on You");

	@HandleEvents
	public void flintedFoehn(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x13C) && !event.getTarget().isThePlayer()) {
			context.accept(flintedFoehn.getModified(event));
		}
	}

	@NpcCastCallout({0x8D0E, 0x8994})
	private final ModifiableCallout<AbilityCastStart> oschonSoaringMinuet = ModifiableCallout.durationBasedCall("Soaring Minuet (Oschon)", "Get Behind");

	@HandleEvents
	public void soaringMinuetFlag(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8D0E, 0x8994)) {
			soaringMinuet = true;
		}
	}

	@NpcCastCallout(value = {0x8990, 0x8991, 0x8992, 0x8993}, suppressMs = 10_000)
	private final ModifiableCallout<AbilityCastStart> oschonClimbingShot = ModifiableCallout.durationBasedCall("Climbing Shot (Oschon)", "Knockback to Safe Corner");

	@NpcCastCallout(0x89A7)
	private final ModifiableCallout<AbilityCastStart> loftyPeaks = ModifiableCallout.durationBasedCall("Lofty Peaks", "Second Phase");

	@NpcCastCallout(0x89A9)
	private final ModifiableCallout<AbilityCastStart> pitonPullNeSw = ModifiableCallout.durationBasedCall("Piton Pull (NE/SW)", "Northeast/Southwest");

	@NpcCastCallout(0x89AA)
	private final ModifiableCallout<AbilityCastStart> pitonPullNwSe = ModifiableCallout.durationBasedCall("Piton Pull (NW/SE)", "Northwest/Southeast");

	@NpcCastCallout(0x89AF)
	private final ModifiableCallout<AbilityCastStart> altitude = ModifiableCallout.durationBasedCall("Altitude", "Go to Safe Zone");

	@NpcCastCallout(0x8CF6)
	private final ModifiableCallout<AbilityCastStart> wanderingShotNorth = ModifiableCallout.durationBasedCall("Wandering Shot (North)", "South, Away from Orb");

	@NpcCastCallout(0x8CF7)
	private final ModifiableCallout<AbilityCastStart> wanderingShotSouth = ModifiableCallout.durationBasedCall("Wandering Shot (South)", "North, Away from Orb");

	@NpcCastCallout(0x89AC)
	private final ModifiableCallout<AbilityCastStart> wanderingVolleyNorth = ModifiableCallout.durationBasedCall("Wandering Volley (North)", "Knockback to South");

	@NpcCastCallout(0x89AD)
	private final ModifiableCallout<AbilityCastStart> wanderingVolleySouth = ModifiableCallout.durationBasedCall("Wandering Volley (South)", "Knockback to North");

	// エウロギア

	@NpcCastCallout(0x8A03)
	private final ModifiableCallout<AbilityCastStart> dawnOfTime = ModifiableCallout.durationBasedCall("Dawn of Time", "Raidwide");

	private static String eulogiaForm(long id) {
		if (id == 0x8A0A || id == 0x8A0B || id == 0x8A0C) {
			return "Left";
		}
		else if (id == 0x8A0D || id == 0x8A0E || id == 0x8A0F) {
			return "Right";
		}
		else if (id == 0x8A10 || id == 0x8A11 || id == 0x8A12) {
			return "In";
		}
		return "Unknown";
	}

	private final ModifiableCallout<AbilityCastStart> eulogiaFormLeft = new ModifiableCallout<>("Forms (Left)", "Left", 4_000);
	private final ModifiableCallout<AbilityCastStart> eulogiaFormRight = new ModifiableCallout<>("Forms (Right)", "Right", 4_000);
	private final ModifiableCallout<AbilityCastStart> eulogiaFormIn = new ModifiableCallout<>("Forms (In)", "In", 4_000);
	private final ModifiableCallout<AbilityCastStart> eulogiaFormSummary = new ModifiableCallout<>("Forms (Summary)", "{form1} => {form2} => {form3}", 19_000);

	@HandleEvents
	public void eulogiaForms(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x8A0A, 0x8A0B, 0x8A0C, 0x8A0D, 0x8A0E, 0x8A0F, 0x8A10, 0x8A11, 0x8A12)) {
			return;
		}
		String form = eulogiaForm(event.getAbility().getId());
		eulogiaForms.add(form);
		// 3つ目の詠唱でまとめて順番を言う
		if (eulogiaForms.size() == EULOGIA_FORMS) {
			Map<String, Object> params = Map.of(
					"form1", eulogiaForms.get(0),
					"form2", eulogiaForms.get(1),
					"form3", eulogiaForms.get(2));
			eulogiaForms.clear();
			context.accept(eulogiaFormSummary.getModified(event, params));
			return;
		}
		ModifiableCallout<AbilityCastStart> call = switch (form) {
			case "Left" -> eulogiaFormLeft;
			case "Right" -> eulogiaFormRight;
			default -> eulogiaFormIn;
		};
		context.accept(call.getModified(event));
	}

	@NpcCastCallout(0x8A2F)
	private final ModifiableCallout<AbilityCastStart> theWhorl = ModifiableCallout.durationBasedCall("The Whorl", "Big Raidwide");

	@NpcCastCallout(0x8A30)
	private final ModifiableCallout<AbilityCastStart> lovesLight = new ModifiableCallout<>("Love's Light", "Start from Bright Moon", 10_000);

	@NpcCastCallout(0x8A37)
	private final ModifiableCallout<AbilityCastStart> hydrostasis = new ModifiableCallout<>("Hydrostasis", "3 => 1 => 2", 13_000);

	@NpcCastCallout(0x8A43)
	private final ModifiableCallout<AbilityCastStart> eulogiaHieroglyphika = new ModifiableCallout<>("Hieroglyphika (Eulogia)", "Go to Rotated Safe Zone", 20_000);

	@NpcCastCallout(0x8A47)
	private final ModifiableCallout<AbilityCastStart> handOfTheDestroyerRed = ModifiableCallout.durationBasedCall("Hand of the Destroyer (Red)", "Blue Half");

	@NpcCastCallout(0x8A48)
	private final ModifiableCallout<AbilityCastStart> handOfTheDestroyerBlue = ModifiableCallout.durationBasedCall("Hand of the Destroyer (Blue)", "Red Half");

	@NpcCastCallout(0x8A4E)
	private final ModifiableCallout<AbilityCastStart> eulogiaTridents = new ModifiableCallout<>("Torrential Tridents (Eulogia)", "Last Trident, then First Trident", 8_000);

	@NpcCastCallout(0x8CFD)
	private final ModifiableCallout<AbilityCastStart> destructiveBolt = ModifiableCallout.durationBasedCall("Destructive Bolt", "Stack on {event.target}");

	@NpcCastCallout(0x8A52)
	private final ModifiableCallout<AbilityCastStart> byregotsStrike = ModifiableCallout.durationBasedCall("Byregot's Strike", "Knockback, Avoid Lightning");

	@NpcCastCallout(0x8A5B)
	private final ModifiableCallout<AbilityCastStart> asAboveRed = ModifiableCallout.durationBasedCall("As Above, So Below (Red)", "Blue is Safe");

	@NpcCastCallout(0x8A5C)
	private final ModifiableCallout<AbilityCastStart> asAboveBlue = ModifiableCallout.durationBasedCall("As Above, So Below (Blue)", "Red is Safe");

	@NpcCastCallout(0x8D0B)
	private final ModifiableCallout<AbilityCastStart> eulogiaClimbingShot = ModifiableCallout.durationBasedCall("Climbing Shot (Eulogia)", "Knockback");

	@NpcCastCallout(0x8A69)
	private final ModifiableCallout<AbilityCastStart> eulogiaSoaringMinuet = ModifiableCallout.durationBasedCall("Soaring Minuet (Eulogia)", "Get Behind");

	@NpcCastCallout(0x8A2C)
	private final ModifiableCallout<AbilityCastStart> eudaimonEorzea = new ModifiableCallout<>("Eudaimon Eorzea", "Raidwide", 18_000);

}
