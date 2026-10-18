package gg.xp.xivsupport.triggers.dtdungeon;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.Position;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/strayborough-deadwalk.ts をもとに移植
@CalloutRepo(name = "The Strayborough Deadwalk", duty = KnownDuty.StrayboroughDeadwalk)
public class StrayboroughDeadwalk extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public StrayboroughDeadwalk(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.StrayboroughDeadwalk);
	}

	private static final long ghostlyGuiseId = 0xF6D;
	// 線は x=130～166 に4間隔で並び、中央の148には幽霊が出ない
	private static final long bitterRegretCenterX = 149;

	private boolean playerIsGhost;
	private boolean solidBitterLinesNext;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		playerIsGhost = false;
		solidBitterLinesNext = false;
	}

	// 1ボス目（His Royal Headness Leonogg I）

	@NpcCastCallout(0x8EB1)
	private final ModifiableCallout<AbilityCastStart> leonoggMaliciousMist = ModifiableCallout.durationBasedCall("Malicious Mist (Leonogg)", "Raidwide");

	@NpcAbilityUsedCallout(value = 0x8EAE, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> fallingNightmare = new ModifiableCallout<>("Falling Nightmare", "Avoid Nightmare Puddles");

	@NpcCastCallout(0x8EF6)
	private final ModifiableCallout<AbilityCastStart> spiritedCharge = ModifiableCallout.durationBasedCall("Spirited Charge", "Avoid Charging Dolls");

	@NpcCastCallout(0x9B02)
	private final ModifiableCallout<AbilityCastStart> evilScheme = ModifiableCallout.durationBasedCall("Evil Scheme", "Avoid Exaflares");

	@PlayerHeadmarker(0xC5)
	private final ModifiableCallout<HeadMarkerEvent> loomingNightmare = new ModifiableCallout<>("Looming Nightmare", "Chasing AoE");

	private final ModifiableCallout<HeadMarkerEvent> lastDrop = new ModifiableCallout<>("Last Drop", "Buster on {event.target}");

	@HandleEvents
	public void lastDropMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xDA)) {
			context.accept(lastDrop.getModified(event));
		}
	}

	// 2ボス目（Jack-in-the-Pot）

	@NpcCastCallout(0x8F75)
	private final ModifiableCallout<AbilityCastStart> sordidSteam = ModifiableCallout.durationBasedCall("Sordid Steam", "Raidwide");

	// 3ボス目（Träumerei）

	@HandleEvents
	public void ghostGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(ghostlyGuiseId) && event.getTarget().isThePlayer()) {
			playerIsGhost = true;
		}
	}

	@HandleEvents
	public void ghostLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(ghostlyGuiseId) && event.getTarget().isThePlayer()) {
			playerIsGhost = false;
		}
	}

	@NpcCastCallout(0x9113)
	private final ModifiableCallout<AbilityCastStart> bitterRegretMiddle = ModifiableCallout.durationBasedCall("Bitter Regret (Middle)", "Sides");

	@NpcCastCallout(0x9114)
	private final ModifiableCallout<AbilityCastStart> bitterRegretSides = ModifiableCallout.durationBasedCall("Bitter Regret (Sides)", "Middle");

	private final ModifiableCallout<AbilityCastStart> illIntentGhost = ModifiableCallout.durationBasedCall("Ill Intent (Ghost)", "Stretch Tether");
	private final ModifiableCallout<AbilityCastStart> illIntentFlesh = ModifiableCallout.durationBasedCall("Ill Intent (Not Ghost)", "Become Ghost then Stretch Tether");

	@HandleEvents
	public void illIntent(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x9AB7) && event.getTarget().isThePlayer()) {
			context.accept((playerIsGhost ? illIntentGhost : illIntentFlesh).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> fleshbusterBecomeGhost = ModifiableCallout.durationBasedCall("Fleshbuster (Not Ghost)", "Become a Ghost");
	private final ModifiableCallout<AbilityCastStart> fleshbusterStayGhost = ModifiableCallout.durationBasedCall("Fleshbuster (Ghost)", "Stay a Ghost");

	@HandleEvents
	public void fleshbuster(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x911C)) {
			context.accept((playerIsGhost ? fleshbusterStayGhost : fleshbusterBecomeGhost).getModified(event));
		}
	}

	// 散開マーカーの見た目だが、正しく処理すればダメージは無く重なっても問題ない
	private final ModifiableCallout<AbilityCastStart> ghostdusterClearGhost = ModifiableCallout.durationBasedCall("Ghostduster (Ghost)", "Clear Ghost Status");
	private final ModifiableCallout<AbilityCastStart> ghostdusterStayFlesh = ModifiableCallout.durationBasedCall("Ghostduster (Not Ghost)", "Avoid Ghost Tiles");

	@HandleEvents
	public void ghostduster(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x9119)) {
			context.accept((playerIsGhost ? ghostdusterClearGhost : ghostdusterStayFlesh).getModified(event));
		}
	}

	@NpcCastCallout(0x9130)
	private final ModifiableCallout<AbilityCastStart> traumereiMaliciousMist = ModifiableCallout.durationBasedCall("Malicious Mist (Träumerei)", "Raidwide with Bleed");

	// 雑魚の Bitter Regret は2種類とも 91DC なので、直前のボスの技で区別する
	@HandleEvents
	public void bitterRegretTracker(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x9119, 0x911C)) {
			solidBitterLinesNext = false;
		}
		else if (event.abilityIdMatches(0x910D)) {
			solidBitterLinesNext = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> bitterRegretAlternate = ModifiableCallout.durationBasedCall("Bitter Regret (Alternating Lines)", "Start Middle then Dodge Lines");
	private final ModifiableCallout<AbilityCastStart> bitterRegretSolidLeft = ModifiableCallout.durationBasedCall("Bitter Regret (Solid Lines, Left Safe)", "Left");
	private final ModifiableCallout<AbilityCastStart> bitterRegretSolidRight = ModifiableCallout.durationBasedCall("Bitter Regret (Solid Lines, Right Safe)", "Right");

	// 2セット目では呼ばない
	private final RepeatSuppressor alternateSupp = new RepeatSuppressor(Duration.ofSeconds(10));
	// 複数体が同時に詠唱する
	private final RepeatSuppressor solidSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void addBitterRegret(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x91DC)) {
			return;
		}
		if (!solidBitterLinesNext) {
			if (alternateSupp.check(event)) {
				context.accept(bitterRegretAlternate.getModified(event));
			}
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos == null) {
			pos = state.getLatestCombatantData(event.getSource()).getPos();
		}
		if (pos == null || !solidSupp.check(event)) {
			return;
		}
		boolean rightUnsafe = Math.round(pos.x()) > bitterRegretCenterX;
		context.accept((rightUnsafe ? bitterRegretSolidLeft : bitterRegretSolidRight).getModified(event));
	}

	// 頭上マーカーは無く、対象者への即時アクションで頭割り対象が分かる
	@NpcAbilityUsedCallout(0x9118)
	private final ModifiableCallout<AbilityUsedEvent> ghostcrusher = new ModifiableCallout<>("Ghostcrusher", "Stack on {event.target}");

}
