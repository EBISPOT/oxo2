import React, { useRef, useState, useEffect } from "react";
import ForceGraph2D from "react-force-graph-2d";
import { InferredMapping } from "../../model/Mapping";

const canvasHeight = 400;

interface Node {
    id: string;
    label: string;
    type: string;
}

interface Link {
    source: string;
    target: string;
    label?: string;
}

interface GraphData {
    nodes: Node[];
    links: Link[];
}

function createMappingId(mapping: InferredMapping): string {
    const data = `${mapping.objectIri}|${mapping.predicateIri}|${mapping.subjectIri}|${mapping.mappingSetId}`;
    return data;
}

function buildGraphData(mapping: InferredMapping): GraphData {
    const nodes: Node[] = [];
    const links: Link[] = [];

    function traverse(current: InferredMapping) {
        const nodeId = createMappingId(current);
        const isAsserted = current.chainRuleApplications?.chainRule?.chainRuleName === "Asserted";
        // Set label as an array of lines for multi-line rendering
        nodes.push({
            id: nodeId,
            label: [
                current.subjectId ?? "",
                current.predicateId ?? "",
                current.objectId ?? ""
            ].join("\n"),
            type: isAsserted ? "asserted" : "inferred"
        });

        const premises = current.chainRuleApplications?.premises;
        if (premises && premises.length > 0) {
            premises.forEach(premise => {
                const premiseId = createMappingId(premise);
                traverse(premise);

                // Escape angle brackets in label for canvas rendering
                let label = current.chainRuleApplications?.chainRule?.chainRuleAbbreviated;
                if (label) {
                    label = label.replace(/</g, '').replace(/>/g, '');
                }

                links.push({
                    source: premiseId,
                    target: nodeId,
                    label
                });
            });
        }
    }

    traverse(mapping);

    return { nodes, links };
}

const InferredMappingForceGraph = ({ explanation }: { explanation: InferredMapping }) => {
    const graphData = buildGraphData(explanation);
    const finalConclusionNodeId = createMappingId(explanation);

    // Rectangle node renderer
    const nodeCanvasObject = (node: any, ctx: CanvasRenderingContext2D, globalScale: number) => {
        ctx.save();
        ctx.font = `${12 / globalScale}px Sans-Serif`;

        // Split label into lines and measure the longest line
        const lines = node.label.split("\n");
        const lineHeight = 14 / globalScale;
        const totalHeight = lineHeight * lines.length;
        const padding = 10;
        let maxWidth = 0;
        lines.forEach((line: string) => {
            const width = ctx.measureText(line).width;
            if (width > maxWidth) maxWidth = width;
        });
        const rectWidth = maxWidth + padding * 2;
        const rectHeight = totalHeight + padding;

        // Draw rectangle
        ctx.beginPath();
        ctx.rect(node.x - rectWidth / 2, node.y - rectHeight / 2, rectWidth, rectHeight);
        ctx.fillStyle = "#f7e7bc"; // Node color
        ctx.fill();
        ctx.strokeStyle = "#f2d890"; // Node border color
        ctx.stroke();
        ctx.closePath();

        // Draw each line of the label
        ctx.fillStyle = "#000";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        lines.forEach((line: string, i: number) => {
            ctx.fillText(
                line,
                node.x,
                node.y - totalHeight / 2 + i * lineHeight + lineHeight / 2
            );
        });
        ctx.restore();
    };

    // Responsive width
    const containerRef = useRef<HTMLDivElement>(null);
    const [width, setWidth] = useState<number>(800);

    useEffect(() => {
        function updateWidth() {
            if (containerRef.current) {
                setWidth(containerRef.current.offsetWidth);
            }
        }
        updateWidth();
        window.addEventListener("resize", updateWidth);
        return () => window.removeEventListener("resize", updateWidth);
    }, []);

    // --- Custom top-down tree layout: firstNodeId at top center, children below recursively ---
    const fgRef = useRef<any>(null);

    useEffect(() => {
        if (!fgRef.current) return;
        const { nodes, links } = graphData;
        if (!nodes.length) return;

        // Get the latest container width directly
        const containerWidth = containerRef.current ? containerRef.current.offsetWidth : width;

        // Map node ids to node objects for quick lookup
        const nodeMap: Record<string, any> = {};
        nodes.forEach(n => { nodeMap[n.id] = n; });

        // Use the first node as the root
        const root = nodeMap[finalConclusionNodeId];
        if (!root) return;

        // Layout parameters
        const levelGap = 20;
        const nodeGap = 160;

        // Track visited to avoid cycles
        const visited = new Set<string>();

        // Recursive function to position nodes in a tree
        function layoutTree(node: any, depth: number, x: number) {
            node.fx = x;
            node.fy = 40 + depth * levelGap;
            visited.add(node.id);

            // Find premises for this conclusion
            const premiseLinks = links.filter(
                l => l.target === node.id && nodeMap[l.source] && !visited.has(l.source)
            );
            const premises = premiseLinks.map(l => nodeMap[l.source]);

            if (premises.length > 0) {
                // Spread premises horizontally
                const totalWidth = (premises.length - 1) * nodeGap;
                premises.forEach((premise, idx) => {
                    layoutTree(premise, depth + 1, x - totalWidth / 2 + idx * nodeGap);
                });
            }
        }

        layoutTree(root, 0, containerWidth / 2);

        // Force update the graph by resetting the cooldownTicks and cooldownTime
        if (fgRef.current && typeof fgRef.current.cooldownTicks === "number") {
            fgRef.current.cooldownTicks = 0;
        }
        if (typeof fgRef.current?.reheatSimulation === "function") {
            fgRef.current.reheatSimulation();
        }
    }, [width, graphData, finalConclusionNodeId]);

    // useEffect(() => {
    //     if (fgRef.current) {
    //         fgRef.current.d3Force('center', null); // Disable center force
    //         fgRef.current.d3Force('charge', null); // Disable charge force to avoid node repulsion
    //     }
    // }, []);

    // useEffect(() => {
    //     if (fgRef.current) {
    //         fgRef.current.zoomToFit(20, 20);
    //     }
    // }, [graphData, width]);

    return (
        <div ref={containerRef} style={{ width: "100%", height: "400px" }}>
            <h1 className="section-subheading">Explanation of inferred mapping</h1>
            <ForceGraph2D
                ref={fgRef}
                graphData={graphData}
                nodeLabel="label"
                linkDirectionalArrowLength={6}
                linkDirectionalArrowRelPos={1}
                linkLabel="label"
                width={width}
                height={canvasHeight}
                nodeCanvasObject={nodeCanvasObject}
                linkColor={() => "#000"}
                linkWidth={() => 3}
            />
        </div>
    );
};

export default InferredMappingForceGraph;
