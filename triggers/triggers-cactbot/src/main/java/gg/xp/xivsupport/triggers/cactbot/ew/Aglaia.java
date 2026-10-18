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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/alliance/aglaia.ts をもとに移植
@CalloutRepo(name = "Aglaia", duty = KnownDuty.Aglaia)
public class Aglaia extends AutoChildEventHandler implements FilteredEventHandler {

	// 複数のタンクに同時に来る詠唱が揃うまで待つ時間
	private static final int COLLECT_MS = 300;
	// アライアンスなのでタンクは最大6人
	private static final int MAX_TANKS = 6;
	// 壊れた世界のポータルが残っている時間
	private static final long BROKEN_WORLD_MS = 20_000;

	private enum NaldColor {
		NONE,
		ORANGE,
		BLUE
	}

	private XivState state;

	public Aglaia(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Aglaia);
	}

	private boolean rhalgrSeenBeacon;
	private AbilityCastStart brokenWorld;
	private boolean smeltingOnMe;
	private boolean arrowOnMe;
	private NaldColor naldLastColor = NaldColor.NONE;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		rhalgrSeenBeacon = false;
		brokenWorld = null;
		smeltingOnMe = false;
		arrowOnMe = false;
		naldLastColor = NaldColor.NONE;
	}

	// 同じ詠唱が複数のタンクを対象に出るので、自分が含まれるかどうかで分ける
	private SequentialTrigger<BaseEvent> tankCleaveSq(long id, ModifiableCallout<AbilityCastStart> onYou, ModifiableCallout<AbilityCastStart> avoid) {
		return SqtTemplates.sq(5_000,
				AbilityCastStart.class, acs -> acs.abilityIdMatches(id),
				(e1, s) -> {
					List<AbilityCastStart> casts = new ArrayList<>();
					casts.add(e1);
					casts.addAll(s.waitEventsUntil(MAX_TANKS,
							AbilityCastStart.class, acs -> acs.abilityIdMatches(id),
							BaseEvent.class, e -> e1.getEffectiveTimeSince().toMillis() > COLLECT_MS));
					boolean onPlayer = casts.stream().anyMatch(acs -> acs.getTarget().isThePlayer());
					s.updateCall(onPlayer ? onYou : avoid, e1);
				});
	}

	// ビエルゴ

	@NpcCastCallout(0x7176)
	private final ModifiableCallout<AbilityCastStart> ordealOfThunder = ModifiableCallout.durationBasedCall("Ordeal of Thunder", "Raidwide");

	@NpcCastCallout(0x725A)
	private final ModifiableCallout<AbilityCastStart> byregotsStrike = ModifiableCallout.durationBasedCall("Byregot's Strike", "Knockback");

	@NpcCastCallout(0x7167)
	private final ModifiableCallout<AbilityCastStart> byregotsStrikeLightning = ModifiableCallout.durationBasedCall("Byregot's Strike (Lightning)", "Knockback with Lightning");

	private final ModifiableCallout<AbilityCastStart> byregotsWardOnYou = ModifiableCallout.durationBasedCall("Byregot's Ward (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> byregotsWardAvoid = ModifiableCallout.durationBasedCall("Byregot's Ward (Not on You)", "Avoid Tank Cleave");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> byregotsWardSq = tankCleaveSq(0x7175, byregotsWardOnYou, byregotsWardAvoid);

	@NpcCastCallout(0x716B)
	private final ModifiableCallout<AbilityCastStart> reproduce = ModifiableCallout.durationBasedCall("Reproduce", "Dodge Normal, then Glowing Row");

	// ラールガーズ・エミッサリー

	@NpcCastCallout(0x70E0)
	private final ModifiableCallout<AbilityCastStart> destructiveStatic = ModifiableCallout.durationBasedCall("Destructive Static", "Get Behind");

	@NpcCastCallout(0x70E3)
	private final ModifiableCallout<AbilityCastStart> boltsFromTheBlue = ModifiableCallout.durationBasedCall("Bolts from the Blue", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> destructiveStrikeOnYou = ModifiableCallout.durationBasedCall("Destructive Strike (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> destructiveStrikeAvoid = ModifiableCallout.durationBasedCall("Destructive Strike (Not on You)", "Avoid Tank Cleave");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> destructiveStrikeSq = tankCleaveSq(0x70D9, destructiveStrikeOnYou, destructiveStrikeAvoid);

	// ラールガー

	@NpcCastCallout(0x70A5)
	private final ModifiableCallout<AbilityCastStart> lightningReign = ModifiableCallout.durationBasedCall("Lightning Reign", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> destructiveBoltOnYou = ModifiableCallout.durationBasedCall("Destructive Bolt (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> destructiveBoltAvoid = ModifiableCallout.durationBasedCall("Destructive Bolt (Not on You)", "Avoid Tank Cleaves");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> destructiveBoltSq = tankCleaveSq(0x70B4, destructiveBoltOnYou, destructiveBoltAvoid);

	private final ModifiableCallout<AbilityCastStart> rhalgrsBeacon = ModifiableCallout.durationBasedCall("Rhalgr's Beacon", "Knockback");
	private final ModifiableCallout<AbilityCastStart> rhalgrsBeaconOrbs = ModifiableCallout.durationBasedCall("Rhalgr's Beacon (Orbs)", "Knockback, Avoid Orbs");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> rhalgrsBeaconSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x70B8),
			(e1, s) -> {
				// 10秒詠唱なので、早すぎないよう半分まで待つ
				s.waitMs(5_000);
				// 2回目以降は玉も一緒に出てくる
				s.updateCall(rhalgrSeenBeacon ? rhalgrsBeaconOrbs : rhalgrsBeacon, e1);
				rhalgrSeenBeacon = true;
			});

	@NpcCastCallout(value = 0x70BA, onYou = true)
	private final ModifiableCallout<AbilityCastStart> lightningStorm = ModifiableCallout.durationBasedCall("Lightning Storm", "Spread");

	@HandleEvents
	public void brokenWorld(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x70A6)) {
			brokenWorld = event;
		}
	}

	private boolean brokenWorldActive() {
		return brokenWorld != null && brokenWorld.getEffectiveTimeSince().toMillis() < BROKEN_WORLD_MS;
	}

	private final ModifiableCallout<AbilityCastStart> handBlue = ModifiableCallout.durationBasedCall("Hand of the Destroyer (Blue)", "Red Half");
	private final ModifiableCallout<AbilityCastStart> handBluePortal = ModifiableCallout.durationBasedCall("Hand of the Destroyer (Blue, Portal)", "Red Half, Away from Portal");

	@HandleEvents
	public void handOfTheDestroyerBlue(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x70A9)) {
			context.accept((brokenWorldActive() ? handBluePortal : handBlue).getModified(event));
		}
	}

	@NpcCastCallout(0x70A8)
	private final ModifiableCallout<AbilityCastStart> handRedInitial = ModifiableCallout.durationBasedCall("Hand of the Destroyer (Red, Initial)", "Blue Half");

	@NpcCastCallout(0x70AC)
	private final ModifiableCallout<AbilityCastStart> handRed = ModifiableCallout.durationBasedCall("Hand of the Destroyer (Red)", "Near Red Portal");

	// 獅子

	// 両方の獅子が詠唱するので1回にまとめる
	@NpcCastCallout(value = 0x71D7, suppressMs = 2_000)
	private final ModifiableCallout<AbilityCastStart> doubleImmolation = ModifiableCallout.durationBasedCall("Double Immolation", "Raidwide");

	@NpcCastCallout(0x71D2)
	private final ModifiableCallout<AbilityCastStart> slashAndBurnLionessFirst = ModifiableCallout.durationBasedCall("Slash and Burn (Lioness First)", "Under Lioness, then Out");

	@NpcCastCallout(0x71D0)
	private final ModifiableCallout<AbilityCastStart> slashAndBurnLionFirst = ModifiableCallout.durationBasedCall("Slash and Burn (Lion First)", "Out, then Under Lioness");

	// アーゼマ

	@NpcCastCallout(0x70A0)
	private final ModifiableCallout<AbilityCastStart> wardensProminence = ModifiableCallout.durationBasedCall("Warden's Prominence", "Raidwide");

	@NpcCastCallout(0x7081)
	private final ModifiableCallout<AbilityCastStart> solarWings = ModifiableCallout.durationBasedCall("Solar Wings", "Front/Back");

	private final ModifiableCallout<AbilityCastStart> wardensWarmthOnYou = ModifiableCallout.durationBasedCall("Warden's Warmth (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> wardensWarmthAvoid = ModifiableCallout.durationBasedCall("Warden's Warmth (Not on You)", "Avoid Tank Cleaves");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> wardensWarmthSq = tankCleaveSq(0x709F, wardensWarmthOnYou, wardensWarmthAvoid);

	@NpcCastCallout(0x709C)
	private final ModifiableCallout<AbilityCastStart> fleetingSpark = ModifiableCallout.durationBasedCall("Fleeting Spark", "Get Behind");

	@NpcCastCallout(0x7098)
	private final ModifiableCallout<AbilityCastStart> sublimeSunset = ModifiableCallout.durationBasedCall("Sublime Sunset", "Away from Orb");

	// ナルザル

	@NpcCastCallout({0x70E8, 0x70E9})
	private final ModifiableCallout<AbilityCastStart> asAboveSoBelow = ModifiableCallout.durationBasedCall("As Above, So Below", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> heatAboveOrange = ModifiableCallout.durationBasedCall("Heat Above, Flames Below (Orange)", "Out");
	private final ModifiableCallout<AbilityCastStart> heatAboveBlue = ModifiableCallout.durationBasedCall("Heat Above, Flames Below (Blue)", "Under");

	@HandleEvents
	public void heatAboveFlamesBelow(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x73A4)) {
			naldLastColor = NaldColor.ORANGE;
			context.accept(heatAboveOrange.getModified(event));
		}
		else if (event.abilityIdMatches(0x73A5)) {
			naldLastColor = NaldColor.BLUE;
			context.accept(heatAboveBlue.getModified(event));
		}
	}

	@PlayerHeadmarker(0xED)
	private final ModifiableCallout<HeadMarkerEvent> smelting = new ModifiableCallout<>("Smelting", "Protean Spread on You");

	@HandleEvents
	public void naldHeadmarkers(EventContext context, HeadMarkerEvent event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.markerIdMatches(0xED)) {
			smeltingOnMe = true;
		}
		else if (event.markerIdMatches(0x154)) {
			arrowOnMe = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> heavensTrial = ModifiableCallout.durationBasedCall("Heavens' Trial", "Stack on {event.target}");

	@HandleEvents
	public void heavensTrial(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x711F)) {
			return;
		}
		// 散開マーカー持ちは頭割りに入らない
		if (!smeltingOnMe) {
			context.accept(heavensTrial.getModified(event));
		}
		smeltingOnMe = false;
	}

	private final ModifiableCallout<AbilityCastStart> goldenTenetOnYou = ModifiableCallout.durationBasedCall("Golden Tenet (On You)", "Shared Buster on You");
	private final ModifiableCallout<AbilityCastStart> goldenTenet = ModifiableCallout.durationBasedCall("Golden Tenet", "Shared Buster on {event.target}");

	@HandleEvents
	public void goldenTenet(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x711B)) {
			context.accept((event.getTarget().isThePlayer() ? goldenTenetOnYou : goldenTenet).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> farAboveBlueArrow = ModifiableCallout.durationBasedCall("Far Above, Deep Below (Blue, Arrow)", "Drop Marker Outside");
	private final ModifiableCallout<AbilityCastStart> farAboveBlue = ModifiableCallout.durationBasedCall("Far Above, Deep Below (Blue)", "Ignore Fake Stack");
	private final ModifiableCallout<AbilityCastStart> farAboveOrangeArrow = ModifiableCallout.durationBasedCall("Far Above, Deep Below (Orange, Arrow)", "Ignore Fake Arrow");

	@HandleEvents
	public void farAboveDeepBelow(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x73AA)) {
			context.accept((arrowOnMe ? farAboveBlueArrow : farAboveBlue).getModified(event));
			naldLastColor = NaldColor.BLUE;
			arrowOnMe = false;
		}
		else if (event.abilityIdMatches(0x73AB)) {
			if (arrowOnMe) {
				context.accept(farAboveOrangeArrow.getModified(event));
			}
			naldLastColor = NaldColor.ORANGE;
			arrowOnMe = false;
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> farFlungFire = new ModifiableCallout<>("Far-flung Fire", "Line Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> farFlungFireSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x73AC),
			(e1, s) -> {
				// 色の詠唱と同時刻に来るが順番が前後することがあるので、少し待ってから色を見る
				s.waitMs(500);
				if (naldLastColor == NaldColor.ORANGE) {
					s.updateCall(farFlungFire, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> onceAboveOrange = ModifiableCallout.durationBasedCall("Once Above, Ever Below (Orange)", "Blue Quadrant");
	private final ModifiableCallout<AbilityCastStart> onceAboveBlue = ModifiableCallout.durationBasedCall("Once Above, Ever Below (Blue)", "Orange Quadrant");

	@HandleEvents
	public void onceAboveEverBelow(EventContext context, AbilityCastStart event) {
		// 73BF/741D は最終的に橙、73C0/741C は最終的に青
		if (event.abilityIdMatches(0x73BF, 0x741D)) {
			naldLastColor = NaldColor.ORANGE;
			context.accept(onceAboveOrange.getModified(event));
		}
		else if (event.abilityIdMatches(0x73C0, 0x741C)) {
			naldLastColor = NaldColor.BLUE;
			context.accept(onceAboveBlue.getModified(event));
		}
	}

	@NpcCastCallout(0x72B7)
	private final ModifiableCallout<AbilityCastStart> hellOfFireFront = ModifiableCallout.durationBasedCall("Hell of Fire (Front)", "Get Behind");

	@NpcCastCallout(0x72B9)
	private final ModifiableCallout<AbilityCastStart> hellOfFireBack = ModifiableCallout.durationBasedCall("Hell of Fire (Back)", "Go Front");

	@NpcCastCallout(value = 0x712D, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> magmaticSpell = ModifiableCallout.durationBasedCall("Magmatic Spell", "Stack Groups");

	private final ModifiableCallout<AbilityCastStart> stygianTenetOnYou = ModifiableCallout.durationBasedCall("Stygian Tenet (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> stygianTenetAvoid = ModifiableCallout.durationBasedCall("Stygian Tenet (Not on You)", "Avoid Tank Cleaves");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> stygianTenetSq = tankCleaveSq(0x711D, stygianTenetOnYou, stygianTenetAvoid);

	private final ModifiableCallout<AbilityCastStart> hearthAboveBlueArrow = ModifiableCallout.durationBasedCall("Hearth Above, Flight Below (Blue, Arrow)", "Under, then Drop Marker Outside");
	private final ModifiableCallout<AbilityCastStart> hearthAboveBlue = ModifiableCallout.durationBasedCall("Hearth Above, Flight Below (Blue)", "Under, Ignore Fake Stack");
	private final ModifiableCallout<AbilityCastStart> hearthAboveOrangeArrow = ModifiableCallout.durationBasedCall("Hearth Above, Flight Below (Orange, Arrow)", "Out, Ignore Fake Arrow");
	private final ModifiableCallout<AbilityCastStart> hearthAboveOrange = ModifiableCallout.durationBasedCall("Hearth Above, Flight Below (Orange)", "Out");

	@HandleEvents
	public void hearthAboveFlightBelow(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x73CA, 0x73CC)) {
			context.accept((arrowOnMe ? hearthAboveBlueArrow : hearthAboveBlue).getModified(event));
			naldLastColor = NaldColor.BLUE;
			arrowOnMe = false;
		}
		else if (event.abilityIdMatches(0x73CB, 0x741B)) {
			context.accept((arrowOnMe ? hearthAboveOrangeArrow : hearthAboveOrange).getModified(event));
			naldLastColor = NaldColor.ORANGE;
		}
	}

	@NpcCastCallout(0x7121)
	private final ModifiableCallout<AbilityCastStart> hellsTrial = ModifiableCallout.durationBasedCall("Hells' Trial", "Raidwide");

}
