package gg.xp.xivsupport.triggers.cactbot.hw;

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
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivPlayerCharacter;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a9s.ts をもとに移植
@CalloutRepo(name = "Alexander The Eyes Of The Creator Savage", duty = KnownDuty.AlexanderTheEyesOfTheCreatorSavage)
public class A9s extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public A9s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheEyesOfTheCreatorSavage);
	}

	// ストックパイルの回数で、雑魚を運ぶ溶岩の位置が決まる
	private int stockpileCount;
	private @Nullable Long mainTankId;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		stockpileCount = 0;
		mainTankId = null;
	}

	@HandleEvents
	public void stockpile(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1A38)) {
			stockpileCount++;
		}
	}

	@HandleEvents
	public void autoAttack(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x1AFE) && !event.getSource().isPc()) {
			mainTankId = event.getTarget().getId();
		}
	}

	private final ModifiableCallout<AbilityCastStart> scraplineOnYou = ModifiableCallout.durationBasedCall("Scrapline (Main Tank)", "Scrapline on You");
	private final ModifiableCallout<AbilityCastStart> scrapline = ModifiableCallout.durationBasedCall("Scrapline", "Get Behind");

	@HandleEvents
	public void scrapline(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1A3C)) {
			XivPlayerCharacter player = state.getPlayer();
			boolean mainTank = mainTankId != null && player != null && mainTankId == player.getId();
			context.accept((mainTank ? scraplineOnYou : scrapline).getModified(event));
		}
	}

	@NpcCastCallout(0x1A3D)
	private final ModifiableCallout<AbilityCastStart> doubleScrapline = ModifiableCallout.durationBasedCall("Double Scrapline", "Stand in Alarum Puddle");

	@PlayerHeadmarker(0x0017)
	private final ModifiableCallout<HeadMarkerEvent> scrapRock = new ModifiableCallout<>("Scrap Rock", "Rock on You");

	private final ModifiableCallout<HeadMarkerEvent> scrapBurst = new ModifiableCallout<>("Scrap Burst", "Hide Fully Behind Rock");

	// 岩が落ちてから隠れる指示を出す
	@AutoFeed
	private final SequentialTrigger<BaseEvent> scrapBurstSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x0017),
			(e1, s) -> {
				s.waitMs(5_000);
				s.updateCall(scrapBurst, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> scrapBomb = new ModifiableCallout<>("Scrap Bomb", "Stack on {event.target}");

	@HandleEvents
	public void scrapBombMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x003E)) {
			context.accept(scrapBomb.getModified(event));
		}
	}

	@PlayerHeadmarker(0x000E)
	private final ModifiableCallout<HeadMarkerEvent> spread = new ModifiableCallout<>("Spread Marker", "Spread");

	private final ModifiableCallout<TetherEvent> addsNortheast = new ModifiableCallout<>("Power Generator: Adds to NE", "Adds to NE Lava");
	private final ModifiableCallout<TetherEvent> addsSoutheast = new ModifiableCallout<>("Power Generator: Adds to SE", "Adds to SE Lava");
	private final ModifiableCallout<TetherEvent> addsSouthwest = new ModifiableCallout<>("Power Generator: Adds to SW", "Adds to SW Lava");
	private final ModifiableCallout<TetherEvent> addsNorthwest = new ModifiableCallout<>("Power Generator: Adds to NW", "Adds to NW Lava");
	private final RepeatSuppressor addTetherSupp = new RepeatSuppressor(Duration.ofSeconds(30));

	@HandleEvents
	public void addTether(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x0011) || !addTetherSupp.check(event)) {
			return;
		}
		// 後半は複数の選択肢があるが、古いコンテンツなので一つに決め打ちする
		ModifiableCallout<TetherEvent> call = switch (stockpileCount) {
			case 1 -> addsNortheast;
			case 2 -> addsSoutheast;
			case 4, 6, 8 -> addsSouthwest;
			case 5, 7 -> addsNorthwest;
			default -> null;
		};
		if (call != null) {
			context.accept(call.getModified(event));
		}
	}
}
